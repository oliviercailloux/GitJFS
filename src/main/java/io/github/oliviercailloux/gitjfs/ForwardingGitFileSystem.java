package io.github.oliviercailloux.gitjfs;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableGraph;
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
import org.eclipse.jgit.lib.ObjectId;

public abstract class ForwardingGitFileSystem extends GitFileSystem {
	protected abstract GitFileSystem delegate();

	@Override
	public URI toUri() {
		final IGitFileSystem delegate = delegate();
		return delegate.toUri();
	}

	@Override
	public GitPath getPath(String first, String... more) {
		final IGitFileSystem delegate = delegate();
		return delegate.getPath(first, more);
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
	public GitPathRoot getPathRoot(String rootStringForm) throws InvalidPathException {
		final IGitFileSystem delegate = delegate();
		return delegate.getPathRoot(rootStringForm);
	}

	@Override
	public GitPath getRelativePath(String... names) throws InvalidPathException {
		final IGitFileSystem delegate = delegate();
		return delegate.getRelativePath(names);
	}

	@Override
	public ImmutableGraph<GitPathRootSha> getCommitsGraph() throws UncheckedIOException {
		final IGitFileSystem delegate = delegate();
		return delegate.getCommitsGraph();
	}

	@Override
	public ImmutableSet<GitPathRootRef> getRefs() throws IOException {
		final IGitFileSystem delegate = delegate();
		return delegate.getRefs();
	}

	@Override
	public GitFileSystemProvider provider() {
		final IGitFileSystem delegate = delegate();
		return delegate.provider();
	}

	@Override
	public void close() throws IOException {
		delegate().close();
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
	public Iterable<Path> getRootDirectories() {
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

}
