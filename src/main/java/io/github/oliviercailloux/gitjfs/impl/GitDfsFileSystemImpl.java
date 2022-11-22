package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verifyNotNull;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;

public class GitDfsFileSystemImpl extends GitFileSystemImpl implements IGitDfsFileSystem {

	/**
	 * This is the same reference as the one in {@link GitFileSystemImpl}. I want to
	 * keep the latter private to make it clear that only {@link GitFileSystemImpl}
	 * is responsible for low-level read operations from the repository.
	 */
	private final DfsRepository repository;

	static GitDfsFileSystemImpl givenUserRepository(GitFileSystemProviderImpl provider, DfsRepository repository) {
		return new GitDfsFileSystemImpl(provider, repository);
	}

	private GitDfsFileSystemImpl(GitFileSystemProviderImpl gitProvider, DfsRepository repository) {
		super(gitProvider, repository, false);
		verifyNotNull(repository.getDescription());
		checkArgument(repository.getDescription().getRepositoryName() != null);
		this.repository = repository;
	}

	@Override
	public DfsRepository getRepository() {
		return repository;
	}

}
