package io.github.oliviercailloux.gitjfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;

public abstract class ForwardingGitFileSystemProvider extends GitFileSystemProvider {

	protected abstract GitFileSystemProvider delegate();

	@Override
	public GitFileFileSystem newFileSystem(URI gitFsUri)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException {
		return delegate().newFileSystem(gitFsUri);
	}

	@Override
	public GitFileFileSystem newFileSystemFromGitDir(Path gitDir)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException {
		return delegate().newFileSystemFromGitDir(gitDir);
	}

	@Override
	public GitFileSystem newFileSystemFromRepository(Repository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, IOException {
		return delegate().newFileSystemFromRepository(repository);
	}

	@Override
	public GitFileFileSystem newFileSystemFromFileRepository(FileRepository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, IOException {
		return delegate().newFileSystemFromFileRepository(repository);
	}

	@Override
	public GitDfsFileSystem newFileSystemFromDfsRepository(DfsRepository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException {
		return delegate().newFileSystemFromDfsRepository(repository);
	}

	@Override
	public String getScheme() {
		return delegate().getScheme();
	}

	@Override
	public GitFileSystem getFileSystem(URI gitFsUri) throws FileSystemNotFoundException {
		final IGitFileSystemProvider delegate = delegate();
		return delegate.getFileSystem(gitFsUri);
	}

	@Override
	public String toString() {
		return delegate().toString();
	}

	@Override
	public IGitFileFileSystem getFileSystemFromGitDir(Path gitDir) throws FileSystemNotFoundException {
		return delegate().getFileSystemFromGitDir(gitDir);
	}

	@Override
	public IGitDfsFileSystem getFileSystemFromRepositoryName(String name) throws FileSystemNotFoundException {
		return delegate().getFileSystemFromRepositoryName(name);
	}

	@Override
	public GitPath getPath(URI gitFsUri) {
		final IGitFileSystemProvider delegate = delegate();
		return delegate.getPath(gitFsUri);
	}

	@Override
	public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
		return delegate().newInputStream(path, options);
	}

	@Override
	public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
		return delegate().newOutputStream(path, options);
	}

	@Override
	public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
		return delegate().newFileChannel(path, options, attrs);
	}

	@Override
	public AsynchronousFileChannel newAsynchronousFileChannel(Path path, Set<? extends OpenOption> options,
			ExecutorService executor, FileAttribute<?>... attrs) throws IOException {
		return delegate().newAsynchronousFileChannel(path, options, executor, attrs);
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
		return delegate().newByteChannel(path, options, attrs);
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		return delegate().newDirectoryStream(dir, filter);
	}

	@Override
	public Path readSymbolicLink(Path link) throws IOException {
		return delegate().readSymbolicLink(link);
	}

	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		return delegate().isSameFile(path, path2);
	}

	@Override
	public boolean isHidden(Path path) throws IOException {
		return delegate().isHidden(path);
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		return delegate().getFileStore(path);
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		delegate().checkAccess(path, modes);
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		return delegate().getFileAttributeView(path, type, options);
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
			throws IOException {
		return delegate().readAttributes(path, type, options);
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		return delegate().readAttributes(path, attributes, options);
	}

}
