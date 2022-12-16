package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.gitjfs.GitFileFileSystem;
import io.github.oliviercailloux.gitjfs.IGitFileFileSystem;
import java.nio.file.Path;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A git file system that obtains its commit data by reading from a git
 * directory.
 *
 * @see GitFileSystemProviderImpl#newFileSystemFromGitDir(Path)
 * @see GitFileSystemProviderImpl#newFileSystemFromFileRepository(FileRepository)
 */
public class GitFileFileSystemImpl extends GitFileFileSystem implements IGitFileFileSystem {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileFileSystemImpl.class);

	static GitFileFileSystemImpl givenUserRepository(GitFileSystemProviderImpl provider, FileRepository repository) {
		return new GitFileFileSystemImpl(provider, repository, false);
	}

	static GitFileFileSystemImpl givenOurRepository(GitFileSystemProviderImpl provider, FileRepository repository) {
		return new GitFileFileSystemImpl(provider, repository, true);
	}

	/**
	 * See {@link GitDfsFileSystemImpl}.
	 */
	private final FileRepository repository;

	private GitFileFileSystemImpl(GitFileSystemProviderImpl gitProvider, FileRepository repository,
			boolean shouldCloseRepository) {
		super(gitProvider, repository, shouldCloseRepository);
		LOGGER.debug("Creating file system given {}, {}, {}.", gitProvider, repository, shouldCloseRepository);
		checkNotNull(repository.getDirectory());
		this.repository = repository;
	}

	/**
	 * @deprecated Temporary workaround.
	 */
	@Deprecated
	public Repository getRepository() {
		return repository;
	}

	@Override
	public Path getGitDir() {
		return repository.getDirectory().toPath();
	}
}
