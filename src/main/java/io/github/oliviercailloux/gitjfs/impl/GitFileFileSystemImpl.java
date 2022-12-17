package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableGraph;
import io.github.oliviercailloux.gitjfs.GitFileFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileSystemProvider;
import io.github.oliviercailloux.gitjfs.GitPath;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootRef;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Set;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitFileFileSystemImpl extends GitFileFileSystem {
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
	private final GitFileSystemImpl delegate;

	private GitFileFileSystemImpl(GitFileSystemProviderImpl gitProvider, FileRepository repository,
			boolean shouldCloseRepository) {
		delegate = new GitFileSystemImpl(gitProvider, repository, shouldCloseRepository);
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

	protected GitFileSystemImpl delegate() {
		return delegate;
	}

	@Override
	public URI toUri() {
		return delegate.provider().getGitFileSystems().toUri(this);
	}

	@Override
	public GitPath getPath(String first, String... more) {
		return delegate.getPath(first, more);
	}

	@Override
	public GitPath getAbsolutePath(String first, String... more) throws InvalidPathException {
		return delegate.getAbsolutePath(first, more);
	}

	@Override
	public GitPath getAbsolutePath(ObjectId commitId, String internalPath1, String... internalPath) {
		return delegate.getAbsolutePath(commitId, internalPath1, internalPath);
	}

	@Override
	public GitPathRootSha getPathRoot(ObjectId commitId) {
		return delegate.getPathRoot(commitId);
	}

	@Override
	public GitPathRootRef getPathRootRef(String rootStringForm) throws InvalidPathException {
		return delegate.getPathRootRef(rootStringForm);
	}

	@Override
	public GitPathRoot getPathRoot(String rootStringForm) throws InvalidPathException {
		return delegate.getPathRoot(rootStringForm);
	}

	@Override
	public GitPath getRelativePath(String... names) throws InvalidPathException {
		return delegate.getRelativePath(names);
	}

	@Override
	public ImmutableGraph<? extends GitPathRootSha> getCommitsGraph() throws UncheckedIOException {
		return delegate.getCommitsGraph();
	}

	@Override
	public ImmutableSet<? extends GitPathRootRef> getRefs() throws IOException {
		return delegate.getRefs();
	}

	@Override
	public GitFileSystemProvider provider() {
		return delegate.provider();
	}

	@Override
	public void close() throws IOException {
		delegate().close();
		delegate.provider().getGitFileSystems().hasBeenClosedEvent(this);
	}

	@Override
	public boolean isOpen() {
		return delegate.isOpen();
	}

	@Override
	public boolean isReadOnly() {
		return delegate.isReadOnly();
	}

	@Override
	public String getSeparator() {
		return delegate.getSeparator();
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		return delegate.getRootDirectories();
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		return delegate.getFileStores();
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		return delegate.supportedFileAttributeViews();
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		return delegate.getPathMatcher(syntaxAndPattern);
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		return delegate.getUserPrincipalLookupService();
	}

	@Override
	public WatchService newWatchService() throws IOException {
		return delegate.newWatchService();
	}

}
