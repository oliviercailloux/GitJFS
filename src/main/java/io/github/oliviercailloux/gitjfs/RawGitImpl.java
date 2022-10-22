package io.github.oliviercailloux.gitjfs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;
import static io.github.oliviercailloux.gitjfs.GitFileSystem.JIM_FS;
import static io.github.oliviercailloux.gitjfs.GitFileSystem.JIM_FS_SLASH;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import io.github.oliviercailloux.gitjfs.GitFileSystem.FollowLinksBehavior;
import io.github.oliviercailloux.gitjfs.GitFileSystem.GitObject;
import io.github.oliviercailloux.gitjfs.GitFileSystem.NoContextAbsoluteLinkException;
import io.github.oliviercailloux.gitjfs.GitFileSystem.NoContextNoSuchFileException;
import io.github.oliviercailloux.gitjfs.GitFileSystem.TreeVisit;
import io.github.oliviercailloux.gitjfs.GitFileSystem.TreeWalkDirectoryStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RawGitImpl implements RawGit {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RawGitImpl.class);

	private final Repository repository;
	private final ObjectReader reader;

	private boolean isOpen;

	private final boolean shouldCloseRepository;

	private final Set<DirectoryStream<GitPath>> toClose;

	public RawGitImpl(Repository repository, boolean shouldCloseRepository) {
		this.repository = checkNotNull(repository);
		this.reader = repository.newObjectReader();
		isOpen = true;
		this.shouldCloseRepository = shouldCloseRepository;
		reader.setAvoidUnreachableObjects(true);
		this.toClose = new LinkedHashSet<>();
	}

	@Override
	public boolean isOpen() {
		return isOpen;
	}

	@Override
	public void close() {
		isOpen = false;

		reader.close();
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
		for (@SuppressWarnings("resource")
		DirectoryStream<GitPath> closeable : toClose) {
			try {
				closeable.close();
			} catch (IOException e) {
				throw new VerifyException("Close should not throw exceptions.", e);
			} catch (RuntimeException e) {
				closeExceptions.add(e);
			}
		}
		try {
			provider().getGitFileSystems().hasBeenClosedEvent(this);
		} catch (RuntimeException e) {
			closeExceptions.add(e);
		}
		if (!closeExceptions.isEmpty()) {
			final RuntimeException first = closeExceptions.remove(0);
			if (!closeExceptions.isEmpty()) {
				LOGGER.error("Further problems while closing: {}.", closeExceptions);
			}
			throw first;
		}
	}

	@Override
	public ImmutableList<Ref> refs() throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		return ImmutableList.copyOf(repository.getRefDatabase().getRefsByPrefix(Constants.R_REFS));
	}

	private Ref ref(String gitRef) throws IOException {
		return repository.exactRef(gitRef);
	}

	private GitObject getGitObject(RevTree rootTree, Path relativePath, FollowLinksBehavior behavior)
			throws IOException, PathCouldNotBeFoundException, NoContextNoSuchFileException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		/**
		 * https://www.eclipse.org/forums/index.php?t=msg&th=1103986
		 *
		 * Set up a stack of trees, starting with a one-entry stack containing the root
		 * tree.
		 *
		 * And a stack of remaining names.
		 *
		 * Pick first name, obtain its tree, push on the tree stack. If not a tree but a
		 * blob, and no remaining name, we’re done. If is a symlink, then that’s even
		 * more remaining names enqueued on the stack. If the name is .., then need to
		 * pop the stack of trees instead of reading. If ".", then just skip it.
		 */
		final Deque<ObjectId> trees = new ArrayDeque<>();
		trees.addFirst(rootTree);

		final Set<TreeVisit> visited = new LinkedHashSet<>();

		final Deque<String> remainingNames = new ArrayDeque<>(
				ImmutableList.copyOf(Iterables.transform(relativePath, Path::toString)));

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
				if (currentName.equals(".")) {
				} else if (currentName.equals("")) {
				} else if (currentName.equals("..")) {
					trees.pop();
					if (trees.isEmpty()) {
						throw new NoContextNoSuchFileException("Attempt to move to parent of root.");
					}
					final ObjectId currentTree = trees.peek();
					treeWalk.reset(currentTree);
					LOGGER.debug("Moving current to the parent of {}.", currentPath);
//					currentPath = currentPath.getNameCount() == 1 ? Path.of("") : currentPath.getParent();
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
								throw new PathCouldNotBeFoundException(String.format(
										"Path '%s' is a link, but I may not follow the links, and the remaining path is '%s'.",
										currentPath, remainingNames));
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
							final ImmutableList<String> targetNames = ImmutableList
									.copyOf(Iterables.transform(target, Path::toString));
							LOGGER.debug("Link found; moving current to the parent of {}; prefixing {} to names.",
									currentPath, targetNames);
							currentPath = currentPath.getParent();
							targetNames.reverse().stream().forEachOrdered(remainingNames::addFirst);
							/**
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
	private Path getLinkTarget(AnyObjectId objectId) throws IOException, NoContextAbsoluteLinkException {
		final String linkContent = new String(getBytes(objectId), StandardCharsets.UTF_8);
		final Path target = JIM_FS.getPath(linkContent);
		if (target.isAbsolute()) {
			throw new NoContextAbsoluteLinkException(Path.of(linkContent));
		}
		return target;
	}

	private byte[] getBytes(AnyObjectId objectId) throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final ObjectLoader fileLoader = reader.open(objectId, Constants.OBJ_BLOB);
		verify(fileLoader.getType() == Constants.OBJ_BLOB);
		final byte[] bytes = fileLoader.getBytes();
		return bytes;
	}

	public ImmutableSet<ObjectId> getCommits() throws UncheckedIOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final ImmutableSet<ObjectId> allCommits;
		try (RevWalk walk = new RevWalk(reader)) {
			/**
			 * Not easy to get really all commits, so we are content with returning only the
			 * ones reachable from some ref: this is the normal behavior of git, it seems
			 * (https://stackoverflow.com/questions/4786972).
			 */
			final List<Ref> refs = repository.getRefDatabase().getRefsByPrefix(Constants.R_REFS);
			walk.setRetainBody(false);
			for (Ref ref : refs) {
				walk.markStart(walk.parseCommit(ref.getLeaf().getObjectId()));
			}
			allCommits = ImmutableSet.copyOf(walk);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return allCommits;
	}

	private RevCommit getRevCommit(ObjectId possibleCommitId)
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

	private RevTree getRevTree(ObjectId treeId) throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final RevTree revTree;
		try (RevWalk walk = new RevWalk(reader)) {
			revTree = walk.parseTree(treeId);
		}
		return revTree;
	}

	private long getSize(GitObject gitObject) throws IOException {
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

	/**
	 * Does nothing with links, i.e., just lists them as any other entries. Just
	 * like the default FS on Linux.
	 */
	@SuppressWarnings("resource")
	private TreeWalkDirectoryStream iterate(RevTree tree) throws IOException {
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

	/**
	 * Note that if this method returns an object id, it means that this object id
	 * exists in the database. But it may be a blob, a tree, … (at least if the
	 * given git ref is a tag, not sure otherwise), see
	 * https://git-scm.com/book/en/v2/Git-Internals-Git-References.
	 */
	private Optional<ObjectId> getObjectId(String gitRef) throws IOException {
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

	private void toClose(DirectoryStream<GitPath> stream) {
		toClose.add(stream);
	}
}
