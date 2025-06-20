package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.base.MoreObjects;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Streams;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.github.oliviercailloux.gitjfs.GitFileSystem;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootRef;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.gitjfs.PathCouldNotBeFoundException;
import io.github.oliviercailloux.jaris.graphs.GraphUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GitFileSystemImpl extends GitFileSystem {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystemImpl.class);

  /**
   * Used instead of {@link NoSuchFileException} at places where we can’t get the string form of the
   * path that is not found (because it is only known to the caller).
   */
  @SuppressWarnings("serial")
  static class NoContextNoSuchFileException extends Exception {

    public NoContextNoSuchFileException() {
      super();
    }

    public NoContextNoSuchFileException(String message) {
      super(message);
    }
  }

  @SuppressWarnings("serial")
  static class NoContextAbsoluteLinkException extends Exception {

    private Path absoluteTarget;

    public NoContextAbsoluteLinkException(Path absoluteTarget) {
      super(absoluteTarget.toString());
      this.absoluteTarget = checkNotNull(absoluteTarget);
      checkArgument(absoluteTarget.isAbsolute());
      checkArgument(absoluteTarget.getFileSystem().equals(FileSystems.getDefault()));
    }

    public Path getTarget() {
      return absoluteTarget;
    }
  }

  static class TreeWalkIterator implements PeekingIterator<GitStringObject> {
    private final TreeWalk walk;
    private Boolean hasNext = null;

    private GitStringObject next;

    public TreeWalkIterator(TreeWalk walk) {
      this.walk = checkNotNull(walk);
      hasNext = null;
      next = null;
    }

    @Override
    public boolean hasNext() throws DirectoryIteratorException {
      if (hasNext != null) {
        return hasNext;
      }

      try {
        hasNext = walk.next();
      } catch (IOException e) {
        verify(hasNext == null);
        throw new DirectoryIteratorException(e);
      }

      if (hasNext) {
        /*
         * Do not use walk.getPathString(): this seems to return not the complete path but the path
         * within this tree (i.e., the name).
         */
        final String name = walk.getNameString();
        final ObjectId objectId = walk.getObjectId(0);
        final FileMode fileMode = walk.getFileMode();
        next = GitStringObject.given(name, objectId, fileMode);
      } else {
        next = null;
      }

      return hasNext;
    }

    @Override
    public GitStringObject peek() throws DirectoryIteratorException {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return next;
    }

    @Override
    public GitStringObject next() throws DirectoryIteratorException {
      final GitStringObject current = peek();
      hasNext = null;
      next = null;
      return current;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    public void close() {
      hasNext = false;
      walk.close();
    }
  }

  static class TreeWalkDirectoryStream implements DirectoryStream<GitStringObject> {
    private final TreeWalk walk;
    private final TreeWalkIterator iterator;
    private boolean returned;
    private boolean closed;

    public TreeWalkDirectoryStream(TreeWalk walk) {
      this.walk = checkNotNull(walk);
      iterator = new TreeWalkIterator(walk);
      returned = false;
      closed = false;
    }

    @Override
    public void close() {
      closed = true;
      iterator.close();
      walk.close();
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
     * Once a directory stream is closed, then further access to the directory, using the Iterator,
     * behaves as if the end of stream has been reached. Due to read-ahead, the Iterator may return
     * one or more elements after the directory stream has been closed.
     * <p>
     * If an I/O error is encountered when accessing the directory then it causes the Iterator's
     * hasNext or next methods to throw DirectoryIteratorException with the IOException as the
     * cause. As stated above, the hasNext method is guaranteed to read-ahead by at least one
     * element. This means that if hasNext method returns true, and is followed by a call to the
     * next method, then it is guaranteed that the next method will not fail with a
     * DirectoryIteratorException.
     */
    @Override
    public PeekingIterator<GitStringObject> iterator() {
      if (returned || closed) {
        throw new IllegalStateException();
      }
      returned = true;
      return iterator;
    }
  }

  static class TreeVisit {
    private final ObjectId objectId;
    private final List<String> remainingNames;

    public TreeVisit(AnyObjectId objectId, List<String> remainingNames) {
      this.objectId = objectId.copy();
      this.remainingNames = checkNotNull(remainingNames);
    }

    public ObjectId getObjectId() {
      return objectId;
    }

    public List<String> getRemainingNames() {
      return remainingNames;
    }

    @Override
    public boolean equals(Object o2) {
      if (!(o2 instanceof TreeVisit)) {
        return false;
      }
      final TreeVisit t2 = (TreeVisit) o2;
      return objectId.equals(t2.objectId) && remainingNames.equals(t2.remainingNames);
    }

    @Override
    public int hashCode() {
      return Objects.hash(objectId, remainingNames);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("objectId", objectId)
          .add("remainingNames", remainingNames).toString();
    }
  }

  static class GitStringObject {
    public static GitStringObject given(String fileName, ObjectId objectId, FileMode fileMode) {
      return new GitStringObject(fileName, objectId, fileMode);
    }

    private final String fileName;
    private final ObjectId objectId;
    private final FileMode fileMode;

    private GitStringObject(String fileName, ObjectId objectId, FileMode fileMode) {
      this.fileName = checkNotNull(fileName);
      this.objectId = objectId;
      this.fileMode = checkNotNull(fileMode);
    }

    String getFileName() {
      return fileName;
    }

    ObjectId getObjectId() {
      return objectId;
    }

    FileMode getFileMode() {
      return fileMode;
    }
  }

  /**
   * Object to associate to a git path, verified to exist in the git file system corresponding to
   * its corresponding path.
   */
  static class GitObject {
    /**
     * @param realPath absolute jim fs path
     */
    public static GitObject given(Path realPath, ObjectId objectId, FileMode fileMode) {
      return new GitObject(realPath, objectId, fileMode);
    }

    private final Path realPath;
    private final ObjectId objectId;
    private final FileMode fileMode;

    private GitObject(Path realPath, ObjectId objectId, FileMode fileMode) {
      this.realPath = checkNotNull(realPath);
      this.objectId = objectId;
      this.fileMode = checkNotNull(fileMode);
    }

    /**
     * @return an absolute jim fs path
     */
    Path getRealPath() {
      return realPath;
    }

    ObjectId getObjectId() {
      return objectId;
    }

    FileMode getFileMode() {
      return fileMode;
    }
  }

  static enum FollowLinksBehavior {
    DO_NOT_FOLLOW_LINKS, FOLLOW_LINKS_BUT_END, FOLLOW_ALL_LINKS
  }

  /**
   * It is crucial to always use the same instance of Jimfs, because Jimfs refuses to resolve paths
   * coming from different instances. And the references to JimFs might be better here rather than
   * in {@link GitFileSystemProviderImpl} because when {@link GitFileSystemProviderImpl} is
   * initialized, we do not want to refer to JimFs, which might not be initialized yet (perhaps this
   * should not create problems, but as it seems logically better to have these references here
   * anyway, I did not investigate).
   */
  static final FileSystem JIM_FS =
      Jimfs.newFileSystem(Configuration.unix().toBuilder().setWorkingDirectory("/").build());

  static final Path JIM_FS_EMPTY = JIM_FS.getPath("");

  static final Path JIM_FS_SLASH = JIM_FS.getPath("/");

  private final GitFileSystemProviderImpl gitProvider;
  private boolean isOpen;
  private final ObjectReader reader;
  private final Repository repository;
  private final boolean shouldCloseRepository;

  private final Set<DirectoryStream<GitPathImpl>> toClose;

  final GitPathRootRefImpl mainSlash =
      new GitPathRootRefImpl(this, GitPathRootImpl.DEFAULT_GIT_REF);
  final GitEmptyPath emptyPath = new GitEmptyPath(mainSlash);

  private ImmutableGraph<GitPathRootShaCached> graph;

  /**
   * Git file system provides low-level access to read operations on a repository (such as
   * retrieving a RevCommit given an id; including with specific exceptions raised by JGit). The
   * higher level access such as reading a Commit and throwing nice user-level exceptions such as
   * {@link NoSuchFileException} is left to elsewhere where possible, e.g. GitPathRoot.
   */
  protected GitFileSystemImpl(GitFileSystemProviderImpl gitProvider, Repository repository,
      boolean shouldCloseRepository) {
    this.gitProvider = checkNotNull(gitProvider);
    this.repository = checkNotNull(repository);
    this.shouldCloseRepository = shouldCloseRepository;
    reader = repository.newObjectReader();
    reader.setAvoidUnreachableObjects(true);
    isOpen = true;
    this.toClose = new LinkedHashSet<>();
    graph = null;
  }

  private ImmutableSet<RevCommit> getCommits(boolean retainBodies) throws IOException {
    if (!isOpen) {
      throw new ClosedFileSystemException();
    }

    final ImmutableSet<RevCommit> allCommits;
    try (RevWalk walk = new RevWalk(reader)) {
      /*
       * Not easy to get really all commits, so we are content with returning only the ones
       * reachable from some ref: this is the normal behavior of git, it seems
       * (https://stackoverflow.com/questions/4786972).
       */
      final List<Ref> refs = repository.getRefDatabase().getRefsByPrefix(Constants.R_REFS);
      walk.setRetainBody(retainBodies);
      for (Ref ref : refs) {
        walk.markStart(walk.parseCommit(ref.getLeaf().getObjectId()));
      }
      allCommits = ImmutableSet.copyOf(walk);
    }
    if (retainBodies) {
      verify(allCommits.stream().allMatch(c -> c.getAuthorIdent() != null));
      verify(allCommits.stream().allMatch(c -> c.getParents() != null));
    }
    return allCommits;
  }

  byte[] getBytes(AnyObjectId objectId) throws IOException {
    if (!isOpen) {
      throw new ClosedFileSystemException();
    }

    final ObjectLoader fileLoader = reader.open(objectId, Constants.OBJ_BLOB);
    verify(fileLoader.getType() == Constants.OBJ_BLOB);
    final byte[] bytes = fileLoader.getBytes();
    return bytes;
  }

  /**
   * Does nothing with links, i.e., just lists them as any other entries. Just like the default FS
   * on Linux.
   */
  @SuppressWarnings("resource")
  TreeWalkDirectoryStream iterate(RevTree tree) throws IOException {
    if (!isOpen) {
      throw new ClosedFileSystemException();
    }

    LOGGER.debug("Iterating over {}.", tree);

    final TreeWalk treeWalk = new TreeWalk(reader);
    treeWalk.addTree(tree);
    treeWalk.setRecursive(false);
    final TreeWalkDirectoryStream dirStream = new TreeWalkDirectoryStream(treeWalk);
    LOGGER.debug("Created stream.");
    return dirStream;
  }

  long getSize(GitObject gitObject) throws IOException {
    if (!isOpen) {
      throw new ClosedFileSystemException();
    }

    final int previousThreshold = reader.getStreamFileThreshold();
    LOGGER.debug("Retrieving size of {}.", gitObject);
    final long size = reader.getObjectSize(gitObject.getObjectId(), ObjectReader.OBJ_ANY);
    LOGGER.debug("Got size: {}.", size);
    reader.setStreamFileThreshold(previousThreshold);
    return size;
  }

  RevTree getRevTree(ObjectId treeId) throws IOException {
    if (!isOpen) {
      throw new ClosedFileSystemException();
    }

    final RevTree revTree;
    try (RevWalk walk = new RevWalk(reader)) {
      revTree = walk.parseTree(treeId);
    }
    return revTree;
  }

  RevCommit getRevCommit(ObjectId possibleCommitId)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    if (!isOpen) {
      throw new ClosedFileSystemException();
    }

    final RevCommit revCommit;
    try (RevWalk walk = new RevWalk(reader)) {
      revCommit = walk.parseCommit(possibleCommitId);
    }
    return revCommit;
  }

  GitObject getGitObject(RevTree rootTree, Path relativePath, FollowLinksBehavior behavior)
      throws IOException, PathCouldNotBeFoundException, NoContextNoSuchFileException {
    if (!isOpen) {
      throw new ClosedFileSystemException();
    }

    /*
     * https://www.eclipse.org/forums/index.php?t=msg&th=1103986
     *
     * Set up a stack of trees, starting with a one-entry stack containing the root tree.
     *
     * And a stack of remaining names.
     *
     * Pick first name, obtain its tree, push on the tree stack. If not a tree but a blob, and no
     * remaining name, we’re done. If is a symlink, then that’s even more remaining names enqueued
     * on the stack. If the name is .., then need to pop the stack of trees instead of reading. If
     * ".", then just skip it.
     */
    final Deque<ObjectId> trees = new ArrayDeque<>();
    trees.addFirst(rootTree);

    final Set<TreeVisit> visited = new LinkedHashSet<>();

    final Deque<String> remainingNames =
        new ArrayDeque<>(ImmutableList.copyOf(Iterables.transform(relativePath, Path::toString)));

    Path currentPath = JIM_FS_SLASH;
    GitObject currentGitObject = GitObject.given(currentPath, rootTree, FileMode.TREE);

    try (TreeWalk treeWalk = new TreeWalk(repository, reader)) {
      treeWalk.addTree(rootTree);
      LOGGER.debug("Starting search for {}, {}.", relativePath, behavior);
      while (!remainingNames.isEmpty()) {
        final TreeVisit visit = new TreeVisit(trees.peek(), ImmutableList.copyOf(remainingNames));
        LOGGER.debug("Adding {} to visited.", visit);
        final boolean added = visited.add(visit);
        if (!added) {
          verify(behavior != FollowLinksBehavior.DO_NOT_FOLLOW_LINKS,
              "Should not cycle when not following links, but seems to cycle anyway: " + visit);
          throw new NoContextNoSuchFileException("Cycle at " + remainingNames);
        }

        final String currentName = remainingNames.pop();
        LOGGER.debug("Considering '{}'.", currentName);
        if (currentName.equals(".") || currentName.equals("")) {
          /* Do nothing. */
        } else if (currentName.equals("..")) {
          trees.pop();
          if (trees.isEmpty()) {
            throw new NoContextNoSuchFileException("Attempt to move to parent of root.");
          }
          final ObjectId currentTree = trees.peek();
          treeWalk.reset(currentTree);
          LOGGER.debug("Moving current to the parent of {}.", currentPath);
          // currentPath = currentPath.getNameCount() == 1 ? Path.of("") : currentPath.getParent();
          currentPath = currentPath.getParent();
          assert currentPath != null;
          currentGitObject = GitObject.given(currentPath, currentTree, FileMode.TREE);
        } else {
          currentPath = currentPath.resolve(currentName);
          LOGGER.debug("Moved current to: {}.", currentPath);

          final String absoluteCurrent = currentPath.toString();
          verify(absoluteCurrent.startsWith("/"));
          final PathFilter filter = PathFilter.create(absoluteCurrent.substring(1));
          treeWalk.setFilter(filter);
          treeWalk.setRecursive(false);

          final boolean toNext = treeWalk.next();
          if (!toNext) {
            throw new NoContextNoSuchFileException("Could not find " + currentPath);
          }
          verify(filter.isDone(treeWalk));

          final FileMode fileMode = treeWalk.getFileMode();
          assert (fileMode != null);
          final ObjectId objectId = treeWalk.getObjectId(0);
          currentGitObject = GitObject.given(currentPath, objectId, fileMode);

          verify(!objectId.equals(ObjectId.zeroId()), absoluteCurrent);

          if (fileMode.equals(FileMode.REGULAR_FILE) || fileMode.equals(FileMode.EXECUTABLE_FILE)) {
            if (!remainingNames.isEmpty()) {
              throw new NoContextNoSuchFileException(String.format(
                  "Path '%s' is a file, but remaining path is '%s'.", currentPath, remainingNames));
            }
          } else if (fileMode.equals(FileMode.GITLINK)) {
            if (!remainingNames.isEmpty()) {
              throw new NoContextNoSuchFileException(
                  String.format("Path '%s' is a git link, but remaining path is '%s'.", currentPath,
                      remainingNames));
            }
          } else if (fileMode.equals(FileMode.SYMLINK)) {
            final boolean followThisLink;
            switch (behavior) {
              case DO_NOT_FOLLOW_LINKS:
                if (!remainingNames.isEmpty()) {
                  throw new PathCouldNotBeFoundException(
                      String.format("Path '%s' is a link, but I may not follow the links, "
                          + "and the remaining path is '%s'.", currentPath, remainingNames));
                }
                followThisLink = false;
                break;
              case FOLLOW_ALL_LINKS:
                followThisLink = true;
                break;
              case FOLLOW_LINKS_BUT_END:
                followThisLink = !remainingNames.isEmpty();
                break;
              default:
                throw new VerifyException();
            }
            if (followThisLink) {
              Path target;
              try {
                target = getLinkTarget(objectId);
              } catch (NoContextAbsoluteLinkException e) {
                throw new PathCouldNotBeFoundException(
                    "Absolute link target encountered: " + e.getTarget());
              }
              final ImmutableList<String> targetNames =
                  ImmutableList.copyOf(Iterables.transform(target, Path::toString));
              LOGGER.debug("Link found; moving current to the parent of {}; prefixing {} to names.",
                  currentPath, targetNames);
              currentPath = currentPath.getParent();
              targetNames.reverse().stream().forEachOrdered(remainingNames::addFirst);
              /*
               * Need to reset, otherwise searching again (in the next iteration) will fail.
               */
              treeWalk.reset(trees.peek());
            }
          } else if (fileMode.equals(FileMode.TREE)) {
            LOGGER.debug("Found tree, entering.");
            trees.addFirst(objectId);
            treeWalk.enterSubtree();
          } else {
            throw new UnsupportedOperationException("Unknown file mode: " + fileMode.toString());
          }
        }
      }
    }
    return currentGitObject;
  }

  /**
   * @return a relative jim fs path
   */
  Path getLinkTarget(AnyObjectId objectId) throws IOException, NoContextAbsoluteLinkException {
    final String linkContent = new String(getBytes(objectId), StandardCharsets.UTF_8);
    final Path target = JIM_FS.getPath(linkContent);
    if (target.isAbsolute()) {
      throw new NoContextAbsoluteLinkException(Path.of(linkContent));
    }
    return target;
  }

  /**
   * Note that if this method returns an object id, it means that this object id exists in the
   * database. But it may be a blob, a tree, … (at least if the given git ref is a tag, not sure
   * otherwise), see https://git-scm.com/book/en/v2/Git-Internals-Git-References.
   */
  Optional<ObjectId> getObjectId(String gitRef) throws IOException {
    if (!isOpen) {
      throw new ClosedFileSystemException();
    }

    final Ref ref = repository.exactRef(gitRef);
    if (ref == null) {
      return Optional.empty();
    }

    verify(ref.getName().equals(gitRef));
    verify(!ref.isSymbolic());
    final ObjectId possibleCommitId = ref.getObjectId();
    verifyNotNull(possibleCommitId);
    return Optional.of(possibleCommitId);
  }

  void toClose(DirectoryStream<GitPathImpl> stream) {
    toClose.add(stream);
  }

  @Override
  public GitPathImpl getPath(String first, String... more) {
    final ImmutableList<String> allNames =
        Stream.concat(Stream.of(first), Stream.of(more)).collect(ImmutableList.toImmutableList());

    final boolean startsWithSlash = allNames.stream().filter(n -> !n.isEmpty()).findFirst()
        .map(s -> s.startsWith("/")).orElse(false);
    if (startsWithSlash) {
      return getAbsolutePath(first, more);
    }

    return GitRelativePath.relative(this, allNames);
  }

  @Override
  public GitPathRootImpl getPathRoot(String rootStringForm) throws InvalidPathException {
    return GitPathRootImpl.given(this, GitRev.stringForm(rootStringForm));
  }

  @Override
  public GitPathRootShaImpl getPathRoot(ObjectId commitId) {
    return new GitPathRootShaImpl(this, GitRev.commitId(commitId), Optional.empty());
  }

  private GitPathRootShaCachedImpl getPathRoot(RevCommit commit) {
    final GitPathRootShaCachedImpl p =
        new GitPathRootShaCachedImpl(this, GitRev.commitId(commit), commit);
    return p;
  }

  @Override
  public GitPathRootRefImpl getPathRootRef(String rootStringForm) throws InvalidPathException {
    return new GitPathRootRefImpl(this, GitRev.stringForm(rootStringForm));
  }

  @Override
  public GitPathImpl getAbsolutePath(String first, String... more) throws InvalidPathException {
    final String rootStringForm;
    final ImmutableList<String> internalPath;
    if (first.contains("//")) {
      final int startDoubleSlash = first.indexOf("//");
      final String beforeMiddleOfDoubleSlash = first.substring(0, startDoubleSlash + 1);
      final String afterMiddleOfDoubleSlash = first.substring(startDoubleSlash + 1);
      rootStringForm = beforeMiddleOfDoubleSlash;
      internalPath = Stream.concat(Stream.of(afterMiddleOfDoubleSlash), Stream.of(more))
          .collect(ImmutableList.toImmutableList());
    } else {
      rootStringForm = first;
      final List<String> givenMore = new ArrayList<>(ImmutableList.copyOf(more));
      if (givenMore.isEmpty()) {
        givenMore.add("/");
      } else if (!givenMore.get(0).startsWith("/")) {
        givenMore.set(0, "/" + givenMore.get(0));
      }
      internalPath = ImmutableList.copyOf(givenMore);
    }
    verify(internalPath.isEmpty() || internalPath.get(0).startsWith("/"));

    final GitRev rev = GitRev.stringForm(rootStringForm);
    final GitPathRootImpl root = GitPathRootImpl.given(this, rev);
    return GitAbsolutePath.givenRoot(root, internalPath);
  }

  @Override
  public GitPathImpl getAbsolutePath(ObjectId commitId, String internalPath1,
      String... internalPath) {
    final String internalPath1StartsRight =
        internalPath1.startsWith("/") ? internalPath1 : "/" + internalPath1;
    final ImmutableList<String> givenMore =
        Streams.concat(Stream.of(internalPath1StartsRight), Stream.of(internalPath))
            .collect(ImmutableList.toImmutableList());
    return GitAbsolutePath.givenRoot(
        new GitPathRootShaImpl(this, GitRev.commitId(commitId), Optional.empty()), givenMore);
  }

  @Override
  public GitPathImpl getRelativePath(String... names) throws InvalidPathException {
    return GitRelativePath.relative(this, ImmutableList.copyOf(names));
  }

  @Override
  public ImmutableGraph<GitPathRootShaCached> graph() throws IOException {
    /*
     * Design choice: this method returns ShaCached instances because (I believe that) need to parse
     * a commit to get its parents; need to parse everything, thus.
     */
    if (graph == null) {
      final ImmutableSet<RevCommit> commits = getCommits(true);

      final MutableGraph<RevCommit> cG = GraphUtils.asGraph(commits, p -> ImmutableList.of(),
          c -> ImmutableList.copyOf(c.getParents()));
      final MutableGraph<GitPathRootShaCached> gr = GraphUtils.transform(cG, this::getPathRoot);
      graph = ImmutableGraph.copyOf(gr);
    }
    return graph;
  }

  boolean computedGraph() {
    return graph != null;
  }

  @Override
  public ImmutableSet<GitPathRootRef> refs() throws IOException {
    if (!isOpen) {
      throw new ClosedFileSystemException();
    }

    final List<Ref> refs = repository.getRefDatabase().getRefsByPrefix(Constants.R_REFS);
    return refs.stream().map(r -> getPathRootRef("/" + r.getName() + "/"))
        .collect(ImmutableSet.toImmutableSet());
  }

  @Override
  public ImmutableSet<DiffEntry> diff(GitPathRoot first, GitPathRoot second)
      throws IOException, NoSuchFileException {
    /*
     * Check same fs (to ensure equality of the underlying readers and repositories). This should
     * not create problems even in case of filtering fses: a filtering fs should receive as
     * arguments paths coming from itself (that is, filtered), as requesting to a filtering fs a
     * diff involving non-filtered paths doesn’t make much sense; and the filtering fs could then
     * delegate using the underlying fses (which should be equal again, by recursion).
     */
    checkArgument(this.equals(first.getFileSystem()));
    checkArgument(this.equals(second.getFileSystem()));
    verify(first.getFileSystem().equals(second.getFileSystem()));

    final CanonicalTreeParser firstTreeIter = new CanonicalTreeParser();
    firstTreeIter.reset(reader, getRevCommit(first.getCommit().id()).getTree().getId());
    final CanonicalTreeParser secondTreeIter = new CanonicalTreeParser();
    secondTreeIter.reset(reader, getRevCommit(second.getCommit().id()).getTree().getId());

    try (Git git = new Git(repository)) {
      final List<DiffEntry> diff =
          git.diff().setNewTree(secondTreeIter).setOldTree(firstTreeIter).call();
      return ImmutableSet.copyOf(diff);
    } catch (GitAPIException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public URI toUri() {
    return gitProvider.getGitFileSystems().toUri(this);
  }

  @Override
  public GitFileSystemProviderImpl provider() {
    return gitProvider;
  }

  @Override
  public ImmutableSet<Path> getRootDirectories() throws UncheckedIOException {
    final ImmutableSet<RevCommit> commits = IO_UNCHECKER.getUsing(() -> getCommits(true));
    return commits.stream().map(this::getPathRoot).collect(ImmutableSet.toImmutableSet());
  }

  @Override
  public Iterable<FileStore> getFileStores() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getSeparator() {
    verify(JIM_FS.getSeparator().equals("/"));
    return "/";
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isOpen() {
    return isOpen;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public WatchService newWatchService() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() {
    isOpen = false;

    List<RuntimeException> closeExceptions = new ArrayList<>();
    try {
      reader.close();
    } catch (RuntimeException e) {
      closeExceptions.add(e);
    }
    if (shouldCloseRepository) {
      try {
        repository.close();
      } catch (RuntimeException e) {
        closeExceptions.add(e);
      }
    }
    //@formatter:off
    for (@SuppressWarnings("resource") DirectoryStream<GitPathImpl> closeable : toClose) {
      //@formatter:on
      try {
        closeable.close();
      } catch (IOException e) {
        throw new VerifyException("Close should not throw exceptions.", e);
      } catch (RuntimeException e) {
        closeExceptions.add(e);
      }
    }
    if (!closeExceptions.isEmpty()) {
      final RuntimeException first = closeExceptions.remove(0);
      if (!closeExceptions.isEmpty()) {
        LOGGER.error("Further problems while closing: {}.", closeExceptions);
      }
      throw first;
    }
  }
}
