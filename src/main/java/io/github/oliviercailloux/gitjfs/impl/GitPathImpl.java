package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.URI_UNCHECKER;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.PeekingIterator;
import io.github.oliviercailloux.gitjfs.GitPath;
import io.github.oliviercailloux.gitjfs.PathCouldNotBeFoundException;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemImpl.FollowLinksBehavior;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemImpl.GitObject;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemImpl.GitStringObject;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemImpl.TreeWalkDirectoryStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.revwalk.RevTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GitPathImpl implements GitPath {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GitPathImpl.class);

  private static final Comparator<GitPathImpl> COMPARATOR =
      Comparator.<GitPathImpl, String>comparing(Path::toString);

  private static final String QUERY_PARAMETER_ROOT = "root";

  private static final String QUERY_PARAMETER_INTERNAL_PATH = "internal-path";

  private static class TransformedPeekingIterator<E> implements PeekingIterator<GitPathImpl> {
    private PeekingIterator<E> delegate;
    private final Function<E, GitPathImpl> transform;

    public TransformedPeekingIterator(PeekingIterator<E> delegate,
        Function<E, GitPathImpl> transform) {
      this.delegate = checkNotNull(delegate);
      this.transform = checkNotNull(transform);
    }

    @Override
    public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override
    public GitPathImpl peek() {
      return transform.apply(delegate.peek());
    }

    @Override
    public GitPathImpl next() {
      return transform.apply(delegate.next());
    }

    @Override
    public void remove() {
      delegate.remove();
    }
  }

  private static class PathIterator implements PeekingIterator<GitPathImpl> {
    private final PeekingIterator<GitPathImpl> unfilteredIterator;
    private final Filter<? super GitPathImpl> filter;
    private GitPathImpl next;

    public PathIterator(PeekingIterator<GitPathImpl> unfilteredIterator,
        Filter<? super GitPathImpl> filter) {
      this.unfilteredIterator = checkNotNull(unfilteredIterator);
      this.filter = checkNotNull(filter);
      next = null;
    }

    @Override
    public GitPathImpl peek() throws DirectoryIteratorException {
      if (next == null) {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
      }
      verify(hasNext());
      verify(next != null);
      return next;
    }

    @Override
    public boolean hasNext() {
      if (next != null) {
        return true;
      }
      boolean accepted = false;
      while (unfilteredIterator.hasNext() && !accepted) {
        next = unfilteredIterator.next();
        verify(next != null);
        try {
          accepted = filter.accept(next);
        } catch (IOException e) {
          throw new DirectoryIteratorException(e);
        }
      }
      return accepted;
    }

    @Override
    public GitPathImpl next() {
      final GitPathImpl current = peek();
      next = null;
      return current;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  static GitPathImpl fromQueryString(GitFileSystemImpl fs, Map<String, String> splitQuery) {
    final Optional<String> rootValue = Optional.ofNullable(splitQuery.get(QUERY_PARAMETER_ROOT));
    final Optional<String> internalPathValue =
        Optional.ofNullable(splitQuery.get(QUERY_PARAMETER_INTERNAL_PATH));

    if (rootValue.isPresent()) {
      final String rootString = rootValue.get();
      checkArgument(internalPathValue.isPresent());
      final String internalPathString = internalPathValue.get();
      final Path internalPath = GitFileSystemImpl.JIM_FS_EMPTY.resolve(internalPathString);
      checkArgument(internalPath.isAbsolute());
      return GitAbsolutePath.givenRoot(GitPathRootImpl.given(fs, GitRev.stringForm(rootString)),
          internalPath);
    }

    final Path internalPath = GitFileSystemImpl.JIM_FS_EMPTY.resolve(internalPathValue.orElse(""));
    return GitRelativePath.relative(fs, internalPath);
  }

  /**
   * Returns a path associated to the same git file system as this path, and to the same root as
   * this path, if any; but referring to the given path.
   *
   * @param internalPath must be absolute iff this path is absolute.
   * @return an absolute path iff this path is absolute.
   */
  GitPathImpl withPath(Path internalPath) {
    if (internalPath.equals(getInternalPath())) {
      return this;
    }

    final GitPathRootImpl root = getRoot();

    if (internalPath.isAbsolute()) {
      checkArgument(root != null);
      return GitAbsolutePath.givenRoot(root, internalPath);
    }

    checkArgument(root == null);
    return GitRelativePath.relative(getFileSystem(), internalPath);
  }

  @Override
  public abstract GitPathImpl toAbsolutePath();

  GitAbsolutePath toAbsolutePathAsAbsolutePath() {
    return (GitAbsolutePath) toAbsolutePath();
  }

  /**
   * Equivalent to relativizing against this path’s root if it has one, or returning itself
   * otherwise. But permits optimization by returning fewer new objects.
   */
  abstract GitRelativePath toRelativePath();

  /**
   * Linux style in-memory path, absolute iff has a root component iff this path is absolute iff
   * this path has a root component, may contain no names ("/", good for root-only paths), may
   * contain a single empty name ("", good for empty paths).
   */
  abstract Path getInternalPath();

  @Override
  @SuppressWarnings("resource")
  public abstract GitFileSystemImpl getFileSystem();

  /**
   * Returns {@code true} iff this path has a root component.
   *
   * @return {@code true} if, and only if, this path is absolute
   */
  @Override
  public abstract boolean isAbsolute();

  /**
   * {@inheritDoc}
   * <p>
   * To obtain the root component that this path (possibly implicitly) refers to, including in the
   * case it is relative, use {@code toAbsolutePath().getRoot()}.
   *
   */
  @Override
  public abstract GitPathRootImpl getRoot();

  @Override
  public int getNameCount() {
    return getInternalPath().getNameCount();
  }

  /**
   * @return a relative path with exactly one name element (which is empty iff this path is the
   *         empty path)
   */
  @Override
  public GitPathImpl getName(int index) {
    final Path name = getInternalPath().getName(index);
    verify(!name.isAbsolute());
    verify(name.getNameCount() == 1);
    return withPath(name);
  }

  @Override
  public GitPathImpl subpath(int beginIndex, int endIndex) {
    final Path subpath = getInternalPath().subpath(beginIndex, endIndex);
    verify(!subpath.isAbsolute());
    return withPath(subpath);
  }

  /**
   * @return a relative path representing the name of the file or directory (an empty path iff this
   *         path is an empty path); or {@code null} if this path has zero elements
   */
  @Override
  public GitPathImpl getFileName() {
    final Path fileName = getInternalPath().getFileName();
    if (fileName == null) {
      return null;
    }
    verify(!fileName.isAbsolute());
    return GitRelativePath.relative(getFileSystem(), fileName);
  }

  @Override
  public GitPathImpl getParent() {
    final Path parent = getInternalPath().getParent();
    if (parent == null) {
      return null;
    }
    return withPath(parent);
  }

  /**
   * Tests if this path starts with the given path.
   *
   * <p>
   * This path <em>starts</em> with the given path if this path’s root component <em>equals</em> the
   * root component of the given path, and this path starts with the same name elements as the given
   * path. If the given path has more name elements than this path then {@code false} is returned.
   *
   * <p>
   * If the given path is associated with a different {@code FileSystem} to this path then
   * {@code false} is returned.
   *
   * @param other the given path
   *
   * @return {@code true} iff this path starts with the given path.
   */
  @Override
  public boolean startsWith(Path other) {
    if (!getFileSystem().equals(other.getFileSystem())) {
      return false;
    }
    final GitPathImpl p2 = (GitPathImpl) other;
    return Objects.equals(getRoot(), p2.getRoot())
        && getInternalPath().startsWith(p2.getInternalPath());
  }

  /**
   * Tests if this path ends with the given path.
   *
   * <p>
   * If the given path has <em>N</em> elements, and no root component, and this path has <em>N</em>
   * or more elements, then this path ends with the given path if the last <em>N</em> elements of
   * each path, starting at the element farthest from the root, are equal.
   *
   * <p>
   * If the given path has a root component then this path ends with the given path if the root
   * component of this path <em>equals</em> the root component of the given path, and the
   * corresponding elements of both paths are equal.
   *
   * <p>
   * If the given path is associated with a different {@code FileSystem} to this path then
   * {@code false} is returned.
   *
   * @param other the given path
   *
   * @return {@code true} iff this path ends with the given path.
   */
  @Override
  public boolean endsWith(Path other) {
    if (!getFileSystem().equals(other.getFileSystem())) {
      return false;
    }
    final GitPathImpl p2 = (GitPathImpl) other;
    final boolean matchRoot = (p2.getRoot() == null || p2.getRoot().equals(getRoot()));
    return matchRoot && getInternalPath().endsWith(p2.getInternalPath());
  }

  /**
   * Returns a path that is this path with redundant name elements eliminated.
   * <p>
   * All occurrences of "{@code .}" are considered redundant. If a "{@code ..}" is preceded by a
   * non-"{@code ..}" name then both names are considered redundant (the process to identify such
   * names is repeated until it is no longer applicable).
   * <p>
   * This method does not access the file system; the path may not locate a file that exists.
   * Eliminating "{@code ..}" and a preceding name from a path may result in the path that locates a
   * different file than the original path. This can arise when the preceding name is a symbolic
   * link.
   *
   * @return the resulting path or this path if it does not contain redundant name elements; an
   *         empty path is returned if this path does not have a root component and all name
   *         elements are redundant
   *
   * @see #getParent
   */
  @Override
  public GitPathImpl normalize() {
    return withPath(getInternalPath().normalize());
  }

  /**
   * Resolves the given path against this path.
   *
   * <p>
   * If the {@code other} parameter is an {@link #isAbsolute() absolute} path (equivalently, if it
   * has a root component), then this method trivially returns {@code other}. If {@code other} is an
   * <i>empty path</i> then this method trivially returns this path. Otherwise this method considers
   * this path to be a directory and resolves the given path against this path: this method
   * <em>joins</em> the given path to this path and returns a resulting path that {@link #endsWith
   * ends} with the given path.
   * <p>
   * This method does not access the file system.
   *
   * @see #relativize
   */
  @Override
  public GitPathImpl resolve(Path other) {
    if (!getFileSystem().equals(other.getFileSystem())) {
      throw new IllegalArgumentException();
    }

    final GitPathImpl p2 = (GitPathImpl) other;

    if (other.isAbsolute()) {
      return p2;
    }

    return withPath(getInternalPath().resolve(p2.getInternalPath()));
  }

  /**
   * Resolves the given path against this path.
   *
   * <p>
   * If the {@code other} parameter represents an {@link #isAbsolute() absolute} path (equivalently,
   * if it starts with a <code>/</code>), then this method returns the git path represented by
   * {@code other}. If {@code other} is an empty string then this method trivially returns this
   * path. Otherwise this method considers this path to be a directory and resolves the given path
   * against this path: this method <em>joins</em> the given path to this path and returns a
   * resulting path that {@link #endsWith ends} with the given path.
   * <p>
   * This is equivalent to converting the given path string to a {@code Path} and resolving it
   * against this {@code Path} in the manner specified by the {@link #resolve(Path) resolve} method.
   * <p>
   * For example, suppose that a path represents "{@code foo/bar}", then invoking this method with
   * the path string "{@code gus}" will result in the git path "{@code foo/bar/gus}".
   * <p>
   * This method does not access the file system.
   *
   * @see #relativize
   */
  @Override
  public GitPathImpl resolve(String other) {
    final boolean startsWithSlash = other.startsWith("/");
    if (startsWithSlash) {
      return getFileSystem().getAbsolutePath(other);
    }
    return withPath(getInternalPath().resolve(other));
  }

  /**
   * Constructs a relative path between this path and a given path.
   *
   * <p>
   * Relativization is the inverse of {@link #resolve(Path) resolution}. This method attempts to
   * construct a {@link #isAbsolute relative} path that when {@link #resolve(Path) resolved} against
   * this path, yields a path that locates the same file as the given path. For example, if this
   * path is {@code "a/b"} and the given path is {@code "a/b/c/d"} then the resulting relative path
   * would be {@code "c/d"}. A relative path between two paths can be constructed iff the two paths
   * both are relative, or both are absolute and have the same root component. If this path and the
   * given path are {@link #equals equal} then an <i>empty path</i> is returned.
   *
   * <p>
   * For any two {@link #normalize normalized} paths <i>p</i> and <i>q</i>, where <i>q</i> does not
   * have a root component, <blockquote> <i>p</i>{@code .relativize(}<i>p</i>
   * {@code .resolve(}<i>q</i>{@code )).equals(}<i>q</i>{@code )} </blockquote>
   *
   * <p>
   * TODO When symbolic links are supported, then whether the resulting path, when resolved against
   * this path, yields a path that can be used to locate the {@link Files#isSameFile same} file as
   * {@code other} is implementation dependent. For example, if this path is {@code "/a/b"} and the
   * given path is {@code "/a/x"} then the resulting relative path may be {@code
   * "../x"}. If {@code "b"} is a symbolic link then is implementation dependent if
   * {@code "a/b/../x"} would locate the same file as {@code "/a/x"}.
   *
   * @param other the path to relativize against this path
   *
   * @return the resulting relative path, or an empty path if both paths are equal
   *
   * @throws IllegalArgumentException if {@code other} is not a {@code Path} that can be relativized
   *         against this path
   */
  @Override
  public GitPathImpl relativize(Path other) {
    if (!getFileSystem().equals(other.getFileSystem())) {
      throw new IllegalArgumentException();
    }
    final GitPathImpl p2 = (GitPathImpl) other;
    if (!Objects.equals(getRoot(), p2.getRoot())) {
      throw new IllegalArgumentException();
    }
    if (p2.getNameCount() == 0) {
      return toRelativePath();
    }
    return GitRelativePath.relative(getFileSystem(),
        getInternalPath().relativize(p2.getInternalPath()));
  }

  /**
   * Returns a URI referring to the git file system instance associated to this path, and referring
   * to this specific file in that file system.
   *
   * @see GitFileSystemProviderImpl#getPath(URI)
   */
  @SuppressWarnings("resource")
  @Override
  public URI toUri() {
    /**
     * I do not use UriBuilder because it performs “contextual encoding of characters not permitted
     * in the corresponding URI component following the rules of the
     * application/x-www-form-urlencoded media type for query parameters”, and therefore encodes /
     * to %2F. This is not required by the spec when not using that media type (as in this case).
     * See https://stackoverflow.com/a/49400367/.
     */
    final String escapedDirAndFile =
        QueryUtils.QUERY_ENTRY_ESCAPER.escape(getInternalPath().toString());
    final StringBuilder queryBuilder = new StringBuilder();
    if (getRoot() != null) {
      queryBuilder.append(QUERY_PARAMETER_ROOT + "="
          + QueryUtils.QUERY_ENTRY_ESCAPER.escape(getRoot().toStaticRev().toString()));
      queryBuilder.append("&" + QUERY_PARAMETER_INTERNAL_PATH + "=" + escapedDirAndFile);
    } else {
      if (!getInternalPath().toString().isEmpty()) {
        queryBuilder.append(QUERY_PARAMETER_INTERNAL_PATH + "=" + escapedDirAndFile);
      }
    }
    final String query = queryBuilder.toString();

    final URI fsUri = getFileSystem().toUri();
    final URI uriBasis = URI_UNCHECKER.getUsing(
        () -> new URI(fsUri.getScheme(), fsUri.getAuthority(), fsUri.getPath(), null, null));
    final String qMark = query.isEmpty() ? "" : "?";
    /**
     * As the query part is encoded already, we don’t want to use the URI constructor with query
     * parameter, which would in turn encode the %-escapers as if they were %-signs.
     */
    return URI.create(uriBasis + qMark + query);
  }

  /**
   * Returns the <em>real</em> path of an existing file.
   *
   * <p>
   * This method derives from this path, an {@link #isAbsolute absolute} path that locates the
   * {@link Files#isSameFile same} file as this path, but with name elements that represent the
   * actual name of the directories and the file: symbolic links are resolved; and redundant name
   * elements are removed.
   *
   * <p>
   * The {@code options} array may be used to indicate how symbolic links are handled. By default,
   * symbolic links are resolved to their final target, thus, the resulting path contains no
   * symbolic links. If the option {@link LinkOption#NOFOLLOW_LINKS NOFOLLOW_LINKS} is present and
   * the given path contains symbolic links, this method throws PathCouldNotBeFoundException.
   *
   * @param options options indicating how symbolic links are handled
   *
   * @return an absolute path that represents the same path as the file located by this object but
   *         with no symbolic links and no special name elements {@code .} or {@code ..}
   *
   * @throws IOException if the file does not exist or an I/O error occurs, or (in deviation from
   *         the spec) if it can’t be determined whether this file exists due to the path containing
   *         symbolic links whereas symbolic links can’t be followed. I have no idea what the spec
   *         wants the implementor to do in such a case, apart perhaps from returning the original
   *         path, which would seem quite surprising to me; and in supplement, the readAttributes
   *         method is supposed (from Files.exists) to throw an IOException when it can’t be
   *         determined whether the file exists for that same reason.
   * @throws SecurityException In case a relevant part of the underlying file system can’t be
   *         accessed
   */
  @Override
  public GitPath toRealPath(LinkOption... options)
      throws IOException, PathCouldNotBeFoundException, NoSuchFileException {
    final boolean followLinks = !ImmutableSet.copyOf(options).contains(LinkOption.NOFOLLOW_LINKS);
    final GitAbsolutePath absolute = toAbsolutePathAsAbsolutePath();
    final GitObject gitObject =
        absolute.getGitObject(followLinks ? FollowLinksBehavior.FOLLOW_ALL_LINKS
            : FollowLinksBehavior.DO_NOT_FOLLOW_LINKS);
    if (!followLinks && gitObject.getFileMode() == FileMode.SYMLINK) {
      throw new PathCouldNotBeFoundException("Path ends with a sym link: " + toString());
    }
    return absolute.withPath(gitObject.getRealPath());
  }

  /**
   * At the moment, throws {@code UnsupportedOperationException}.
   */
  @Override
  public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers)
      throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * Compares path according to their string form. The spec mandates “lexicographical” comparison,
   * but I ignore what this means: apparently not that the root components must be considered first,
   * then the rest, considering that the OpenJdk implementation of the Linux default file system
   * sorts "" before "/" before "a"; and probably not that they must be sorted by lexicographic
   * ordering of their string form, as it would then be a bit odd to specify that “The ordering
   * defined by this method is provider specific”.
   */
  @Override
  public int compareTo(Path other) {
    if (!getFileSystem().equals(other.getFileSystem())) {
      throw new IllegalArgumentException();
    }
    final GitPathImpl p2 = (GitPathImpl) other;
    return COMPARATOR.compare(this, p2);
  }

  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof GitPath)) {
      return false;
    }
    final GitPath p2 = (GitPath) o2;
    return getFileSystem().equals(p2.getFileSystem()) && toString().equals(p2.toString());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getFileSystem(), toString());
  }

  @Override
  public String toString() {
    final String rootStr = getRoot() == null ? "" : getRoot().toStaticRev().toString();
    return rootStr + getInternalPath().toString();
  }

  @SuppressWarnings("resource")
  DirectoryStream<GitPathImpl> newDirectoryStream(Filter<? super GitPathImpl> filter)
      throws IOException {
    final GitPathRootShaImpl sha = toAbsolutePathAsAbsolutePath().getRoot().toSha();
    /**
     * Note: this can’t be moved to GitAbsolutePath: a directory stream on a relative path differs
     * by resolving against a relative path.
     */
    final Path absoluteRealInternalPath = toAbsolutePathAsAbsolutePath()
        .getGitObject(FollowLinksBehavior.FOLLOW_ALL_LINKS).getRealPath();
    final RevTree tree = toAbsolutePathAsAbsolutePath().getRevTree(true);
    final TreeWalkDirectoryStream directoryStream = getFileSystem().iterate(tree);

    final DirectoryStream<GitPathImpl> toReturn = new DirectoryStream<>() {
      @Override
      public void close() throws IOException {
        directoryStream.close();
      }

      /**
       * As requested per the contract of {@link DirectoryStream}, invoking the iterator method to
       * obtain a second or subsequent iterator throws IllegalStateException.
       * <p>
       * An important property of the directory stream's Iterator is that its hasNext method is
       * guaranteed to read-ahead by at least one element. If hasNext method returns true, and is
       * followed by a call to the next method, it is guaranteed that the next method will not throw
       * an exception due to an I/O error, or because the stream has been closed. The Iterator does
       * not support the remove operation.
       * <p>
       * Once a directory stream is closed, then further access to the directory, using the
       * Iterator, behaves as if the end of stream has been reached. Due to read-ahead, the Iterator
       * may return one or more elements after the directory stream has been closed.
       * <p>
       * If an I/O error is encountered when accessing the directory then it causes the Iterator's
       * hasNext or next methods to throw DirectoryIteratorException with the IOException as the
       * cause. As stated above, the hasNext method is guaranteed to read-ahead by at least one
       * element. This means that if hasNext method returns true, and is followed by a call to the
       * next method, then it is guaranteed that the next method will not fail with a
       * DirectoryIteratorException.
       */
      @Override
      public Iterator<GitPathImpl> iterator() {
        final PeekingIterator<GitStringObject> namesIterator = directoryStream.iterator();
        final TransformedPeekingIterator<GitStringObject> unfilteredPathIterator =
            new TransformedPeekingIterator<>(namesIterator,
                g -> toNewEntry(sha, absoluteRealInternalPath, g));
        return new PathIterator(unfilteredPathIterator, filter);
      }
    };
    getFileSystem().toClose(toReturn);
    return toReturn;
  }

  /**
   * @return the same kind (relative VS absolute) as this instance, representing an entry in this
   *         directory.
   */
  GitPathImpl toNewEntry(GitPathRootShaImpl sha, Path absoluteRealInternalPath,
      GitStringObject localGitObject) {
    final String name = localGitObject.getFileName();
    final Path newInternal = getInternalPath().resolve(name);
    final GitPathImpl newPath = withPath(newInternal);
    final GitObject resolved = GitObject.given(absoluteRealInternalPath.resolve(name),
        localGitObject.getObjectId(), localGitObject.getFileMode());
    ((GitAbsolutePathWithInternal) newPath.toAbsolutePathAsAbsolutePath()).setRealGitObject(sha,
        resolved);
    return newPath;
  }

  GitPath resolveRelativePath(String directoryEntry) {
    verify(!directoryEntry.isEmpty());
    verify(!directoryEntry.startsWith("/"));
    return resolve(directoryEntry);
  }
}
