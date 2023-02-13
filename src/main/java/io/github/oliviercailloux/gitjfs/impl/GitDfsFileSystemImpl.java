package io.github.oliviercailloux.gitjfs.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verifyNotNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableGraph;
import io.github.oliviercailloux.gitjfs.GitDfsFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileSystemProvider;
import io.github.oliviercailloux.gitjfs.GitPath;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootRef;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Set;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.lib.ObjectId;

public class GitDfsFileSystemImpl extends GitDfsFileSystem {

  /**
   * This is the same reference as the one in {@link GitFileSystemImpl}. I want to keep the latter
   * private to make it clear that only {@link GitFileSystemImpl} is responsible for low-level read
   * operations from the repository.
   */
  private final DfsRepository repository;
  private final GitFileSystemImpl delegate;

  static GitDfsFileSystemImpl givenUserRepository(GitFileSystemProviderImpl provider,
      DfsRepository repository) {
    return new GitDfsFileSystemImpl(provider, repository);
  }

  private GitDfsFileSystemImpl(GitFileSystemProviderImpl gitProvider, DfsRepository repository) {
    delegate = new GitFileSystemImpl(gitProvider, repository, false);
    verifyNotNull(repository.getDescription());
    checkArgument(repository.getDescription().getRepositoryName() != null);
    this.repository = repository;
  }

  protected GitFileSystemImpl delegate() {
    return delegate;
  }

  @Override
  public DfsRepository getRepository() {
    return repository;
  }

  @Override
  public URI toUri() {
    return delegate.toUri();
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
  public ImmutableGraph<GitPathRootSha> graph() throws IOException {
    return delegate.graph();
  }

  @Override
  public ImmutableSet<GitPathRootRef> refs() throws IOException {
    return delegate.refs();
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
  public ImmutableSet<Path> getRootDirectories() {
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

  @Override
  public ImmutableSet<DiffEntry> diff(GitPathRoot first, GitPathRoot second) throws IOException {
    return delegate.diff(first, second);
  }
}
