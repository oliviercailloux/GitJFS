package io.github.oliviercailloux.gitjfs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Streams;
import com.google.common.graph.ImmutableGraph;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.github.oliviercailloux.jaris.collections.GraphUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * A git file system. Associated to a git repository. Can be used to obtain
 * {@link GitPath} instances.
 * </p>
 * <p>
 * Must be {@link #close() closed} to release resources associated with readers.
 * </p>
 * <p>
 * Reads links transparently, as indicated in the package-summary of the nio
 * package. Thus, assuming dir is a symlink to otherdir, reading dir/file.txt
 * reads otherdir/file.txt. This is also what git operations do naturally:
 * checking out dir will restore it as a symlink to otherdir
 * (https://stackoverflow.com/a/954575). Consider implementing
 * Provider#readLinks and so on.
 *
 * Note that a git repository does not have the concept of hard links
 * (https://stackoverflow.com/a/3731139).
 *
 *
 * @see #getAbsolutePath(String, String...)
 * @see #getRelativePath(String...)
 * @see #getGitRootDirectories()
 */
public abstract class GitFileSystem extends FileSystem {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystem.class);

	/**
	 * Used instead of {@link NoSuchFileException} at places where we can’t get the
	 * string form of the path that is not found (because it is only known to the
	 * caller).
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
				/**
				 * Do not use walk.getPathString(): this seems to return not the complete path
				 * but the path within this tree (i.e., the name).
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
		 * As requested per the contract of {@link DirectoryStream}, invoking the
		 * iterator method to obtain a second or subsequent iterator throws
		 * IllegalStateException.
		 * <p>
		 * An important property of the directory stream's Iterator is that its hasNext
		 * method is guaranteed to read-ahead by at least one element. If hasNext method
		 * returns true, and is followed by a call to the next method, it is guaranteed
		 * that the next method will not throw an exception due to an I/O error, or
		 * because the stream has been closed. The Iterator does not support the remove
		 * operation.
		 * <p>
		 * Once a directory stream is closed, then further access to the directory,
		 * using the Iterator, behaves as if the end of stream has been reached. Due to
		 * read-ahead, the Iterator may return one or more elements after the directory
		 * stream has been closed.
		 * <p>
		 * If an I/O error is encountered when accessing the directory then it causes
		 * the Iterator's hasNext or next methods to throw DirectoryIteratorException
		 * with the IOException as the cause. As stated above, the hasNext method is
		 * guaranteed to read-ahead by at least one element. This means that if hasNext
		 * method returns true, and is followed by a call to the next method, then it is
		 * guaranteed that the next method will not fail with a
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
			return MoreObjects.toStringHelper(this).add("objectId", objectId).add("remainingNames", remainingNames)
					.toString();
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
	 * Object to associate to a git path, verified to exist in the git file system
	 * corresponding to its corresponding path.
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

	private final GitFileSystemProvider gitProvider;
	final GitPathRootRef mainSlash = new GitPathRootRef(this, GitPathRoot.DEFAULT_GIT_REF);
	final GitEmptyPath emptyPath = new GitEmptyPath(mainSlash);
	private final RawGitImpl rawGit;
	/**
	 * It is crucial to always use the same instance of Jimfs, because Jimfs refuses
	 * to resolve paths coming from different instances. And the references to JimFs
	 * might be better here rather than in {@link GitFileSystemProvider} because
	 * when {@link GitFileSystemProvider} is initialized, we do not want to refer to
	 * JimFs, which might not be initialized yet (perhaps this should not create
	 * problems, but as it seems logically better to have these references here
	 * anyway, I did not investigate).
	 */
	static final FileSystem JIM_FS = Jimfs
			.newFileSystem(Configuration.unix().toBuilder().setWorkingDirectory("/").build());
	static final Path JIM_FS_EMPTY = JIM_FS.getPath("");
	static final Path JIM_FS_SLASH = JIM_FS.getPath("/");

	/**
	 * Git file system provides low-level access to read operations on a repository
	 * (such as retrieving a RevCommit given an id; including with specific
	 * exceptions raised by JGit). The higher level access such as reading a Commit
	 * and throwing nice user-level exceptions such as {@link NoSuchFileException}
	 * is left to elsewhere where possible, e.g. GitPathRoot.
	 */
	protected GitFileSystem(GitFileSystemProvider gitProvider, Repository repository, boolean shouldCloseRepository) {
		this.gitProvider = checkNotNull(gitProvider);
		rawGit = new RawGitImpl(repository, shouldCloseRepository);
	}

	/**
	 * Converts a path string, or a sequence of strings that when joined form a path
	 * string, to a {@code GitPath}. If {@code first} starts with {@code /} (or if
	 * {@code first} is empty and the first non-empty string in {@code more} starts
	 * with {@code /}), this method behaves as if
	 * {@link #getAbsolutePath(String, String...)} had been called. Otherwise, it
	 * behaves as if {@link #getRelativePath(String...)} had been called.
	 * <p>
	 * No check is performed to ensure that the path refers to an existing git
	 * object in this git file system.
	 * </p>
	 *
	 * @param first the path string or initial part of the path string
	 * @param more  additional strings to be joined to form the path string
	 *
	 * @return the resulting {@code Path}
	 *
	 * @throws InvalidPathException If the path string cannot be converted
	 */
	@Override
	public GitPath getPath(String first, String... more) {
		final ImmutableList<String> allNames = Stream.concat(Stream.of(first), Stream.of(more))
				.collect(ImmutableList.toImmutableList());

		final boolean startsWithSlash = allNames.stream().filter(n -> !n.isEmpty()).findFirst()
				.map(s -> s.startsWith("/")).orElse(false);
		if (startsWithSlash) {
			return getAbsolutePath(first, more);
		}

		return GitRelativePath.relative(this, allNames);
	}

	/**
	 * <p>
	 * Converts an absolute git path string, or a sequence of strings that when
	 * joined form an absolute git path string, to an absolute {@code GitPath}.
	 * </p>
	 * <p>
	 * If {@code more} does not specify any elements then the value of the
	 * {@code first} parameter is the path string to convert.
	 * </p>
	 * <p>
	 * If {@code more} specifies one or more elements then each non-empty string,
	 * including {@code first}, is considered to be a sequence of name elements (see
	 * {@link Path}) and is joined to form a path string using {@code /} as
	 * separator. If {@code first} does not end with {@code //} (but ends with
	 * {@code /}, as required), and if {@code more} does not start with {@code /},
	 * then a {@code /} is added so that there will be two slashes joining
	 * {@code first} to {@code more}.
	 * </p>
	 * <p>
	 * For example, if {@code getAbsolutePath("/refs/heads/main/","foo","bar")} is
	 * invoked, then the path string {@code "/refs/heads/main//foo/bar"} is
	 * converted to a {@code Path}.
	 * </p>
	 * <p>
	 * No check is performed to ensure that the path refers to an existing git
	 * object in this git file system.
	 * </p>
	 *
	 * @param first the string form of the root component, possibly followed by
	 *              other path segments. Must start with <tt>/refs/</tt> or
	 *              <tt>/heads/</tt> or <tt>/tags/</tt> or be a slash followed by a
	 *              40-characters long sha-1; must contain at most once {@code //};
	 *              if does not contain {@code //}, must end with {@code /}.
	 * @param more  may start with {@code /}.
	 * @return an absolute git path.
	 * @throws InvalidPathException if {@code first} does not contain a syntaxically
	 *                              valid root component
	 * @see GitPath
	 */
	public GitPath getAbsolutePath(String first, String... more) throws InvalidPathException {
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
		final GitPathRoot root = GitPathRoot.given(this, rev);
		return GitAbsolutePath.givenRoot(root, internalPath);
	}

	/**
	 * Returns a git path referring to a commit designated by its id. No check is
	 * performed to ensure that the commit exists.
	 *
	 * @param commitId      the commit to refer to
	 * @param internalPath1 may start with a slash.
	 * @param internalPath  may start with a slash.
	 * @return an absolute path
	 * @see GitPath
	 */
	public GitPath getAbsolutePath(ObjectId commitId, String internalPath1, String... internalPath) {
		final String internalPath1StartsRight = internalPath1.startsWith("/") ? internalPath1 : "/" + internalPath1;
		final ImmutableList<String> givenMore = Streams
				.concat(Stream.of(internalPath1StartsRight), Stream.of(internalPath))
				.collect(ImmutableList.toImmutableList());
		return GitAbsolutePath.givenRoot(new GitPathRootSha(this, GitRev.commitId(commitId), Optional.empty()),
				givenMore);
	}

	/**
	 * Returns an absolute git path. No check is performed to ensure that the ref
	 * exists, or that the commit this refers to exists.
	 *
	 * @param rootStringForm the string form of the root component. Must start with
	 *                       <tt>/refs/</tt> or <tt>/heads/</tt> or <tt>/tags/</tt>
	 *                       or be a 40-characters long sha-1 surrounded by slash
	 *                       characters; must end with <tt>/</tt>; may not contain
	 *                       <tt>//</tt> nor <tt>\</tt>.
	 * @return a git path root
	 * @throws InvalidPathException if {@code rootStringForm} does not contain a
	 *                              syntaxically valid root component
	 * @see GitPathRoot
	 */
	public GitPathRoot getPathRoot(String rootStringForm) throws InvalidPathException {
		return GitPathRoot.given(this, GitRev.stringForm(rootStringForm));
	}

	public GitPathRootRef getPathRootRef(String rootStringForm) throws InvalidPathException {
		return new GitPathRootRef(this, GitRev.stringForm(rootStringForm));
	}

	/**
	 * Returns a git path referring to a commit designated by its id. No check is
	 * performed to ensure that the commit exists.
	 *
	 * @param commitId the commit to refer to
	 * @return a git path root
	 * @see GitPathRoot
	 */
	public GitPathRootSha getPathRoot(ObjectId commitId) {
		return new GitPathRootSha(this, GitRev.commitId(commitId), Optional.empty());
	}

	/**
	 * <p>
	 * Converts a relative git path string, or a sequence of strings that when
	 * joined form a relative git path string, to a relative {@code GitPath}.
	 * </p>
	 * <p>
	 * Each non-empty string in {@code names} is considered to be a sequence of name
	 * elements (see {@link Path}) and is joined to form a path string using
	 * {@code /} as separator.
	 * </p>
	 * <p>
	 * For example, if {@code getRelativePath("foo","bar")} is invoked, then the
	 * path string {@code "foo/bar"} is converted to a {@code Path}.
	 * </p>
	 * <p>
	 * An <em>empty</em> path is returned iff names contain only empty strings. It
	 * then implicitly refers to the main branch of this git file system.
	 * </p>
	 * <p>
	 * No check is performed to ensure that the path refers to an existing git
	 * object in this git file system.
	 * </p>
	 *
	 * @param names the internal path; its first element (if any) may not start with
	 *              {@code /}.
	 * @return a relative git path.
	 * @throws InvalidPathException if the first non-empty string in {@code names}
	 *                              start with {@code /}.
	 * @see GitPath
	 */
	public GitPath getRelativePath(String... names) throws InvalidPathException {
		return GitRelativePath.relative(this, ImmutableList.copyOf(names));
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Retrieve the set of all commits of this repository. Consider calling rather
	 * {@code {@link #getCommitsGraph()}.getNodes()}, whose type is more precise.
	 *
	 * @return absolute path roots referring to commit ids.
	 * @throws UncheckedIOException if an I/O error occurs (I have no idea why the
	 *                              Java Files API does not want an IOException
	 *                              here)
	 */
	@Override
	public ImmutableSet<Path> getRootDirectories() throws UncheckedIOException {
		final ImmutableSet<ObjectId> commits = rawGit.getCommits();
		final ImmutableSet<Path> paths = commits.stream().map(this::getPathRoot).collect(ImmutableSet.toImmutableSet());
		return paths;
	}

	/**
	 * Retrieve the set of all commits of this repository reachable from some ref.
	 * This is equivalent to calling {@link #getRootDirectories()}, but with a more
	 * precise type.
	 *
	 * @return absolute path roots, all referring to commit ids (no ref).
	 * @throws UncheckedIOException if an I/O error occurs (using an Unchecked
	 *                              variant to mimic the behavior of
	 *                              {@link #getRootDirectories()})
	 */
	public ImmutableGraph<GitPathRootSha> getCommitsGraph() throws UncheckedIOException {
		final ImmutableSet<ObjectId> commits = rawGit.getCommits();
		final ImmutableSet<GitPathRootSha> paths = commits.stream().map(this::getPathRoot)
				.collect(ImmutableSet.toImmutableSet());

		final Function<GitPathRootSha, List<GitPathRootSha>> getParents = IO_UNCHECKER
				.wrapFunction(p -> p.getParentCommits());

		return ImmutableGraph.copyOf(GraphUtils.asGraph(paths, p -> ImmutableList.of(), getParents::apply));
	}

	/**
	 * Returns a set containing one git path root for each git ref (of the form
	 * <tt>/refs/…</tt>) contained in this git file system. This does not consider
	 * HEAD or other special references, but considers both branches and tags.
	 *
	 * @return git path roots referencing git refs (not commit ids).
	 *
	 * @throws IOException if an I/O error occurs
	 */
	public ImmutableSet<GitPathRootRef> getRefs() throws IOException {
		final List<Ref> refs = rawGit.refs();
		return refs.stream().map(r -> getPathRootRef("/" + r.getName() + "/")).collect(ImmutableSet.toImmutableSet());
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
		return rawGit.isOpen();
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
	public GitFileSystemProvider provider() {
		return gitProvider;
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		throw new UnsupportedOperationException();
	}

	/**
	 * <p>
	 * Returns a gitjfs URI that identifies this git file system, and this specific
	 * git file system instance while it is open.
	 * </p>
	 * <p>
	 * While this instance is open, giving the returned URI to
	 * {@link GitFileSystemProvider#getFileSystem(URI)} will return this file system
	 * instance; giving it to {@link GitFileSystemProvider#getPath(URI)} will return
	 * the default path associated to this file system.
	 * </p>
	 *
	 * @return the URI that identifies this file system.
	 */
	public URI toUri() {
		return gitProvider.getGitFileSystems().toUri(this);
	}

}
