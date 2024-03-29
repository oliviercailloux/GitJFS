package io.github.oliviercailloux.gitjfs;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableGraph;
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
import org.eclipse.jgit.lib.ObjectId;

@SuppressWarnings("resource")
public abstract class ForwardingGitDfsFileSystem extends GitDfsFileSystem {
  protected abstract GitDfsFileSystem delegate();

  @Override
  public GitPath getPath(String first, String... more) {
    final IGitFileSystem delegate = delegate();
    return delegate.getPath(first, more);
  }

  @Override
  public GitPathRoot getPathRoot(String rootStringForm) throws InvalidPathException {
    final IGitFileSystem delegate = delegate();
    return delegate.getPathRoot(rootStringForm);
  }

  @Override
  public GitPathRootSha getPathRoot(ObjectId commitId) {
    final IGitFileSystem delegate = delegate();
    return delegate.getPathRoot(commitId);
  }

  @Override
  public GitPathRootRef getPathRootRef(String rootStringForm) throws InvalidPathException {
    final IGitFileSystem delegate = delegate();
    return delegate.getPathRootRef(rootStringForm);
  }

  @Override
  public GitPath getAbsolutePath(String first, String... more) throws InvalidPathException {
    final IGitFileSystem delegate = delegate();
    return delegate.getAbsolutePath(first, more);
  }

  @Override
  public GitPath getAbsolutePath(ObjectId commitId, String internalPath1, String... internalPath) {
    final IGitFileSystem delegate = delegate();
    return delegate.getAbsolutePath(commitId, internalPath1, internalPath);
  }

  @Override
  public GitPath getRelativePath(String... names) throws InvalidPathException {
    final IGitFileSystem delegate = delegate();
    return delegate.getRelativePath(names);
  }

  @Override
  public ImmutableGraph<GitPathRootShaCached> graph() throws IOException {
    final IGitFileSystem delegate = delegate();
    return delegate.graph();
  }

  @Override
  public ImmutableSet<GitPathRootRef> refs() throws IOException {
    final IGitFileSystem delegate = delegate();
    return delegate.refs();
  }

  @Override
  public ImmutableSet<DiffEntry> diff(GitPathRoot first, GitPathRoot second) throws IOException {
    final IGitFileSystem delegate = delegate();
    return delegate.diff(first, second);
  }

  @Override
  public URI toUri() {
    final IGitFileSystem delegate = delegate();
    return delegate.toUri();
  }

  @Override
  public GitFileSystemProvider provider() {
    final IGitFileSystem delegate = delegate();
    return delegate.provider();
  }

  @Override
  public boolean isOpen() {
    final IGitFileSystem delegate = delegate();
    return delegate.isOpen();
  }

  @Override
  public boolean isReadOnly() {
    final IGitFileSystem delegate = delegate();
    return delegate.isReadOnly();
  }

  @Override
  public String getSeparator() {
    final IGitFileSystem delegate = delegate();
    return delegate.getSeparator();
  }

  @Override
  public ImmutableSet<Path> getRootDirectories() {
    final IGitFileSystem delegate = delegate();
    return delegate.getRootDirectories();
  }

  @Override
  public Iterable<FileStore> getFileStores() {
    final IGitFileSystem delegate = delegate();
    return delegate.getFileStores();
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    final IGitFileSystem delegate = delegate();
    return delegate.supportedFileAttributeViews();
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    final IGitFileSystem delegate = delegate();
    return delegate.getPathMatcher(syntaxAndPattern);
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    final IGitFileSystem delegate = delegate();
    return delegate.getUserPrincipalLookupService();
  }

  @Override
  public WatchService newWatchService() throws IOException {
    final IGitFileSystem delegate = delegate();
    return delegate.newWatchService();
  }

  @Override
  public void close() throws IOException {
    delegate().close();
  }
}
