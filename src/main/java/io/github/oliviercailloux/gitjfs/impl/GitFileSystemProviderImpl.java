package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import io.github.oliviercailloux.gitjfs.AbsoluteLinkException;
import io.github.oliviercailloux.gitjfs.GitFileFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileSystemProvider;
import io.github.oliviercailloux.gitjfs.IGitDfsFileSystem;
import io.github.oliviercailloux.gitjfs.IGitFileSystem;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemImpl.FollowLinksBehavior;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemImpl.GitObject;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotLinkException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A (partial) implementation of {@link FileSystemProvider}, able to produce
 * instances of {@link GitFileSystemImpl}.
 *
 * @see #getInstance()
 * @see #newFileSystemFromGitDir(Path)
 * @see #newFileSystemFromDfsRepository(DfsRepository)
 */
public class GitFileSystemProviderImpl extends GitFileSystemProvider {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystemProviderImpl.class);

	static final String SCHEME = "gitjfs";
	private static GitFileSystemProviderImpl instance;

	/**
	 * Obtains the same instance as the one reachable through the
	 * {@link FileSystemProvider#installedProviders() installed providers}. Calling
	 * this method causes the default provider to be initialized (if not already
	 * initialized) and loads any other installed providers as described by the
	 * {@link FileSystems} class.
	 *
	 * @return the instance of this class registered for this scheme.
	 */
	public static GitFileSystemProvider getInstance() {
		if (instance == null) {
			FileSystemProvider.installedProviders();
			verify(instance != null);
		}
		return instance;
	}

	private final GitFileSystems fses;

	/**
	 * Zero argument constructor to satisfy the standard Java service-provider
	 * loading mechanism.
	 *
	 * @deprecated It is highly recommended to use {@link #getInstance()} instead of
	 *             this constructor. This constructor creates a new instance of the
	 *             provider, which might not be the one used automatically through
	 *             the installed providers mechanism, thereby possibly leading to
	 *             two provider instances running for the same scheme, using two
	 *             distinct caches for git file systems. Access through this
	 *             constructor may be denied in future versions of this library.
	 * @see #getInstance()
	 */
	@Deprecated
	public GitFileSystemProviderImpl() {
		fses = new GitFileSystems();
		if (instance != null) {
			LOGGER.warn("Already one instance, please instanciate only once.");
		} else {
			instance = this;
		}
	}

	@Override
	public String getScheme() {
		return SCHEME;
	}

	GitFileSystems getGitFileSystems() {
		return fses;
	}

	@Override
	public GitFileFileSystem newFileSystem(URI gitFsUri)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException {
		final Path gitDir = fses.getGitDir(gitFsUri);
		return newFileSystemFromGitDir(gitDir);
	}

	@Deprecated
	@Override
	public GitFileFileSystemImpl newFileSystem(URI gitFsUri, Map<String, ?> env)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException {
		return newFileSystem(gitFsUri);
	}

	@Deprecated
	@Override
	public GitFileFileSystemImpl newFileSystem(Path gitDir, Map<String, ?> env)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException {
		return newFileSystemFromGitDir(gitDir);
	}

	@Override
	@SuppressWarnings("resource")
	public GitFileFileSystemImpl newFileSystemFromGitDir(Path gitDir)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException {
		/**
		 * Implementation note: this method also throws UnsupportedOperationException if
		 * the path exists but is not associated with the default file system. But this
		 * is not part of the public contract.
		 */

		fses.verifyCanCreateFileSystemCorrespondingTo(gitDir);

		if (!Files.exists(gitDir)) {
			/**
			 * Not clear whether the specs mandate UnsupportedOperationException here. I
			 * follow the observed behavior of ZipFileSystemProvider in not throwing UOE:
			 * https://github.com/openjdk/jdk17/blob/master/src/jdk.zipfs/share/classes/jdk/nio/zipfs/ZipFileSystemProvider.java,
			 * https://github.com/openjdk/jdk11/blob/master/src/jdk.zipfs/share/classes/jdk/nio/zipfs/ZipFileSystemProvider.java.
			 *
			 * ZipFSP throws a FileSystemNotFoundException but I favor NoSuchFileE, more
			 * explicit and aligned to observed behavior of default FS (default FS throws
			 * NSFE when invoked with a non existent path and throws
			 * IllegalArgumentException when invoked with any URI whose path component is
			 * not / – the latter is inappropriate here as non existant file is a dynamic
			 * property).
			 */
			throw new NoSuchFileException(String.format("Directory %s not found.", gitDir));
		}
		final FileRepository repo = (FileRepository) new FileRepositoryBuilder().setGitDir(gitDir.toFile()).build();
		try {
			if (!repo.getObjectDatabase().exists()) {
				throw new UnsupportedOperationException(String.format("Object database not found in %s.", gitDir));
			}
			final GitFileFileSystemImpl newFs = GitFileFileSystemImpl.givenOurRepository(this, repo);
			fses.put(gitDir, newFs);
			return newFs;
		} catch (Exception e) {
			try {
				repo.close();
			} catch (Exception closing) {
				LOGGER.debug("Exception (suppressed) while closing underlying repository.", closing);
			}
			throw e;
		}

	}

	@Override
	public IGitFileSystem newFileSystemFromRepository(Repository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, IOException {
		if (repository instanceof DfsRepository) {
			final DfsRepository dfs = (DfsRepository) repository;
			return newFileSystemFromDfsRepository(dfs);
		}
		if (repository instanceof FileRepository) {
			final FileRepository f = (FileRepository) repository;
			return newFileSystemFromFileRepository(f);
		}
		throw new IllegalArgumentException("Unknown repository");
	}

	@Override
	@SuppressWarnings("unused")
	public GitFileFileSystemImpl newFileSystemFromFileRepository(FileRepository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, IOException {
		final Path gitDir = repository.getDirectory().toPath();
		fses.verifyCanCreateFileSystemCorrespondingTo(gitDir);

		if (!repository.getObjectDatabase().exists()) {
			throw new UnsupportedOperationException(String.format("Object database not found in %s.", gitDir));
		}
		final GitFileFileSystemImpl newFs = GitFileFileSystemImpl.givenUserRepository(this, repository);
		fses.put(gitDir, newFs);
		return newFs;
	}

	@Override
	public IGitDfsFileSystem newFileSystemFromDfsRepository(DfsRepository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException {
		fses.verifyCanCreateFileSystemCorrespondingTo(repository);

		if (!repository.getObjectDatabase().exists()) {
			throw new UnsupportedOperationException(String.format("Object database not found."));
		}

		final GitDfsFileSystemImpl newFs = GitDfsFileSystemImpl.givenUserRepository(this, repository);
		fses.put(repository, newFs);
		return newFs;
	}

	@Override
	public GitFileSystemImpl getFileSystem(URI gitFsUri) throws FileSystemNotFoundException {
		return fses.getFileSystem(gitFsUri);
	}

	@Override
	public GitFileFileSystemImpl getFileSystemFromGitDir(Path gitDir) throws FileSystemNotFoundException {
		return fses.getFileSystemFromGitDir(gitDir);
	}

	@Override
	public GitDfsFileSystemImpl getFileSystemFromRepositoryName(String name) throws FileSystemNotFoundException {
		return fses.getFileSystemFromName(name);
	}

	@Override
	@SuppressWarnings("resource")
	public GitPathImpl getPath(URI gitFsUri) {
		final GitFileSystemImpl fs = fses.getFileSystem(gitFsUri);
		return GitPathImpl.fromQueryString(fs, QueryUtils.splitQuery(gitFsUri));
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
		checkArgument(path instanceof GitPathImpl);
		if (attrs.length >= 1) {
			throw new ReadOnlyFileSystemException();
		}
		final ImmutableSet<? extends OpenOption> unsupportedOptions = Sets
				.difference(options, ImmutableSet.of(StandardOpenOption.READ, StandardOpenOption.SYNC)).immutableCopy();
		if (!unsupportedOptions.isEmpty()) {
			LOGGER.error("Unknown options: {}.", unsupportedOptions);
			throw new ReadOnlyFileSystemException();
		}

		final GitPathImpl gitPath = (GitPathImpl) path;
		return gitPath.toAbsolutePathAsAbsolutePath().newByteChannel(true);
	}

	@SuppressWarnings("resource")
	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		checkArgument(dir instanceof GitPathImpl);
		final GitPathImpl gitPath = (GitPathImpl) dir;
		final DirectoryStream<GitPathImpl> newDirectoryStream = gitPath.newDirectoryStream(filter);
		return new DirectoryStream<>() {

			@Override
			public void close() throws IOException {
				newDirectoryStream.close();
			}

			@Override
			public Iterator<Path> iterator() {
				return Iterators.transform(newDirectoryStream.iterator(), (p) -> p);
			}
		};
	}

	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isHidden(Path path) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes)
			throws ReadOnlyFileSystemException, AccessDeniedException, NoSuchFileException, IOException {
		checkArgument(path instanceof GitPathImpl);
		final GitPathImpl gitPath = (GitPathImpl) path;

		final ImmutableSet<AccessMode> modesList = ImmutableSet.copyOf(modes);
		if (modesList.contains(AccessMode.WRITE)) {
			throw new ReadOnlyFileSystemException();
		}
		if (!Sets.difference(modesList, ImmutableSet.of(AccessMode.READ, AccessMode.EXECUTE)).isEmpty()) {
			throw new UnsupportedOperationException();
		}

		final GitObject gitObject = gitPath.toAbsolutePathAsAbsolutePath()
				.getGitObject(FollowLinksBehavior.FOLLOW_ALL_LINKS);

		if (modesList.contains(AccessMode.EXECUTE)) {
			if (!Objects.equals(gitObject.getFileMode(), FileMode.EXECUTABLE_FILE)) {
				throw new AccessDeniedException(gitPath.toString());
			}
		}
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
			throws IOException {
		checkArgument(path instanceof GitPathImpl);
		final GitPathImpl gitPath = (GitPathImpl) path;

		if (!type.equals(BasicFileAttributes.class)) {
			throw new UnsupportedOperationException();
		}

		final ImmutableSet<LinkOption> optionsSet = ImmutableSet.copyOf(options);

		return (A) gitPath.toAbsolutePathAsAbsolutePath().readAttributes(optionsSet);
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
			throws ReadOnlyFileSystemException {
		throw new ReadOnlyFileSystemException();
	}

	@Override
	public Path readSymbolicLink(Path link)
			throws IOException, NoSuchFileException, NotLinkException, AbsoluteLinkException, SecurityException {
		checkArgument(link instanceof GitPathImpl);

		final GitPathImpl gitPath = (GitPathImpl) link;
		return gitPath.toAbsolutePathAsAbsolutePath().readSymbolicLink();
	}

}
