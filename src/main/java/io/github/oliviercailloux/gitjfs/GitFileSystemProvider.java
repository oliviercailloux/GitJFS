package io.github.oliviercailloux.gitjfs;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotLinkException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;

public interface GitFileSystemProvider {

	/**
	 * Obtains the same instance as the one reachable through the
	 * {@link FileSystemProvider#installedProviders() installed providers}. Calling
	 * this method causes the default provider to be initialized (if not already
	 * initialized) and loads any other installed providers as described by the
	 * {@link FileSystems} class.
	 *
	 * @return the instance of this class registered for this scheme.
	 */
	public static GitFileSystemProviderImpl provider() {
		return GitFileSystemProviderImpl.getInstance();
	}

	String getScheme();

	/**
	 * <p>
	 * Returns a new git file system reading from a git directory.
	 * </p>
	 * <p>
	 * The given URI must be a gitjfs URI referring to a git directory that no open
	 * git file system reads from. That is to say that any git file system reading
	 * from that directory and that have been created by this provider must be
	 * closed.
	 * </p>
	 * <p>
	 * Such an URI can have been obtained by a call to
	 * {@link GitFileFileSystem#toUri()}, or by a call to
	 * {@link GitPathImpl#toUri()} on an instance of a git path associated with a
	 * {@link GitFileFileSystem} instance. In both cases, under condition of having
	 * used the same version of this library.
	 * </p>
	 * <p>
	 * Thus, an URI obtained during a previous VM execution may be used as well, but
	 * an URI obtained using a given version of this library is in general not
	 * guaranteed to be useable in another version. Please open an issue and
	 * describe your use case if this raises a problem.
	 * </p>
	 *
	 * @param gitFsUri a gitjfs URI referring to a git directory.
	 * @return a new git file system, reading from the git directory that the URI
	 *         refers to.
	 * @throws FileSystemAlreadyExistsException if a git file system is registered
	 *                                          already for that path (in which
	 *                                          case, use
	 *                                          {@link #getFileSystemFromGitDir(Path)}).
	 * @throws UnsupportedOperationException    if the path exists but does not seem
	 *                                          to correspond to a git directory.
	 * @throws NoSuchFileException              if the path does not exist.
	 * @throws IOException                      if an exception occurred during
	 *                                          access to the underlying file
	 *                                          system.
	 */
	GitFileFileSystem newFileSystem(URI gitFsUri)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException;

	/**
	 * Behaves, currently, as if {@link #newFileSystem(URI)} had been called.
	 *
	 * @deprecated This method is there to reflect the {@link FileSystemProvider}
	 *             contract. Because the {@code env} parameter is currently not
	 *             used, it is clearer, and more future-proof, to use
	 *             {@link #newFileSystem(URI)}.
	 */
	@Deprecated
	GitFileFileSystem newFileSystem(URI gitFsUri, Map<String, ?> env)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException;

	/**
	 * Behaves, currently, as if {@link #newFileSystemFromGitDir(Path)} had been
	 * called.
	 *
	 * @deprecated This method is there to reflect the {@link FileSystemProvider}
	 *             contract. Because the {@code env} parameter is currently not
	 *             used, it is clearer, and more future-proof, to use
	 *             {@link #newFileSystemFromGitDir(Path)}.
	 */
	@Deprecated
	GitFileFileSystem newFileSystem(Path gitDir, Map<String, ?> env)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException;

	/**
	 * <p>
	 * Returns a new git file system reading from the given directory.
	 * </p>
	 * <p>
	 * The given directory is the place where git stores the data. It is typically
	 * named “.git”, but this is not mandatory.
	 * </p>
	 * <p>
	 * In the current version of this library, it must be associated with the
	 * {@link FileSystems#getDefault() default} file system because of
	 * <a href="https://www.eclipse.org/forums/index.php/m/1828091/">limitations</a>
	 * of JGit (please vote
	 * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=526500">here</a> to
	 * voice your concern: the bug will already be <a href=
	 * "https://bugs.eclipse.org/bugs/buglist.cgi?component=JGit&resolution=---&order=votes%20DESC">quite
	 * visible</a> if we reach 5 votes).
	 * </p>
	 *
	 * @param gitDir the directory to read data from.
	 * @return a new git file system.
	 * @throws FileSystemAlreadyExistsException if a git file system is registered
	 *                                          already for that path (in which
	 *                                          case, use
	 *                                          {@link #getFileSystemFromGitDir(Path)}).
	 * @throws UnsupportedOperationException    if the path exists but does not seem
	 *                                          to correspond to a git directory.
	 * @throws NoSuchFileException              if the path does not exist.
	 * @throws IOException                      if an exception occurred during
	 *                                          access to the underlying file
	 *                                          system.
	 */
	GitFileFileSystem newFileSystemFromGitDir(Path gitDir)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException;

	/**
	 * <p>
	 * Returns a new git file system reading data from the provided JGit repository
	 * object.
	 * </p>
	 * <p>
	 * Because the repository is provided by the caller, it is the caller’s
	 * responsibility to close it when not needed any more: closing the returned
	 * file system will not close the underlying repository. Thus, when done with
	 * the given repository, it is necessary to close both the returned file system
	 * and the provided repository.
	 * </p>
	 *
	 * @param repository the repository to read data from.
	 * @return a new git file system.
	 * @throws FileSystemAlreadyExistsException if a git file system is registered
	 *                                          already for that repository or for
	 *                                          the path this repository reads from.
	 * @throws UnsupportedOperationException    if the repository contains no git
	 *                                          data.
	 * @throws IOException                      if an exception occurred during
	 *                                          access to the underlying data.
	 */
	GitFileSystemImpl newFileSystemFromRepository(Repository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, IOException;

	/**
	 * <p>
	 * Returns a new git file system reading data from the provided JGit repository
	 * object.
	 * </p>
	 * <p>
	 * Because the repository is provided by the caller, it is the caller’s
	 * responsibility to close it when not needed any more: closing the returned
	 * file system will not close the underlying repository. Thus, when done with
	 * the given repository, it is necessary to close both the returned file system
	 * and the provided repository.
	 * </p>
	 *
	 * @param repository the repository to read data from.
	 * @return a new git file system.
	 * @throws FileSystemAlreadyExistsException if a git file system is registered
	 *                                          already for that repository or for
	 *                                          the path this repository reads from.
	 * @throws UnsupportedOperationException    if the repository contains no git
	 *                                          data.
	 * @throws IOException                      if an exception occurred during
	 *                                          access to the underlying data.
	 */
	GitFileFileSystem newFileSystemFromFileRepository(FileRepository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, IOException;

	/**
	 * <p>
	 * Returns a new git file system reading data from the provided JGit repository
	 * object.
	 * </p>
	 * <p>
	 * Because the repository is provided by the caller, it is the caller’s
	 * responsibility to close it when not needed any more: closing the returned
	 * file system will not close the underlying repository. Thus, when done with
	 * the given repository, it is necessary to close both the returned file system
	 * and the provided repository.
	 * </p>
	 *
	 * @param repository the repository to read data from.
	 * @return a new git file system.
	 * @throws FileSystemAlreadyExistsException if a git file system is registered
	 *                                          already for that repository.
	 * @throws UnsupportedOperationException    if the repository contains no git
	 *                                          data.
	 */
	GitDfsFileSystem newFileSystemFromDfsRepository(DfsRepository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException;

	/**
	 * <p>
	 * Returns an already existing git file system created previously by this
	 * provider.
	 * </p>
	 * <p>
	 * The given URI must have been returned by a call to
	 * {@link GitFileSystemImpl#toUri()} on a git file system instance created by
	 * this provider and that is still open; or by {@link GitPathImpl#toUri()} on a
	 * git path instance associated to a git file system created by this provider
	 * and that is still open.
	 * </p>
	 * <p>
	 * (The wording of the contract for
	 * {@link FileSystemProvider#getFileSystem(URI)} suggests that this method
	 * should return only those file systems that have been created by an explicit
	 * invocation of {@link #newFileSystem(URI, Map)}, and no other method, which
	 * contradict the present implementation. But this restriction does not seem
	 * justified, and the OpenJDK implementation of the default provider does not
	 * satisfy it, so I take it to be an imprecise wording.)
	 * </p>
	 *
	 * @param gitFsUri the uri as returned by {@link GitFileSystemImpl#toUri()} or
	 *                 {@link GitPathImpl#toUri()}.
	 * @return an already existing, open git file system.
	 * @throws FileSystemNotFoundException if no corresponding file system is found.
	 */
	GitFileSystemImpl getFileSystem(URI gitFsUri) throws FileSystemNotFoundException;

	/**
	 * <p>
	 * Returns an already existing git file system created previously by this
	 * provider.
	 * </p>
	 * <p>
	 * There must be an open git file system whose
	 * {@link GitFileFileSystem#getGitDir()} method returns a path with the same
	 * absolute path as {@code gitDir}. In other cases, no guarantee is given.
	 * </p>
	 *
	 * @param gitDir the git directory as returned by
	 *               {@link GitFileFileSystem#getGitDir()}.
	 * @return an already existing, open git file system.
	 * @throws FileSystemNotFoundException if no corresponding file system is found.
	 */
	GitFileFileSystem getFileSystemFromGitDir(Path gitDir) throws FileSystemNotFoundException;

	/**
	 * <p>
	 * Returns an already existing git file system created previously by this
	 * provider.
	 * </p>
	 * <p>
	 * There must be an open git {@link GitDfsFileSystem} file system resting on a
	 * {@link DfsRepository} whose {@link DfsRepository#getDescription()
	 * description} contains the given name.
	 * </p>
	 *
	 * @param name the name of the repository of the git file system to retrieve.
	 * @return an already existing, open git file system.
	 * @throws FileSystemNotFoundException if no corresponding file system is found.
	 */
	GitDfsFileSystem getFileSystemFromRepositoryName(String name) throws FileSystemNotFoundException;

	/**
	 * <p>
	 * Returns a {@code Path} object by converting the given {@link URI}. The given
	 * uri must have been returned by {@link GitPathImpl#toUri()} on a path
	 * associated to an open git file system created by this provider, or directly
	 * by {@link GitFileSystemImpl#toUri()} on an open git file system created by
	 * this provider.
	 * </p>
	 * <p>
	 * This method does not access the underlying file system and requires no
	 * specific permission.
	 * </p>
	 * <p>
	 * This method does not create a new file system transparently, as this would
	 * encourage the caller to forget closing the just created file system (see also
	 * <a href="https://stackoverflow.com/a/16213815">this</a> discussion).
	 * </p>
	 *
	 * @param gitFsUri The URI to convert
	 *
	 * @return The resulting {@code Path}
	 *
	 * @throws IllegalArgumentException    If the given URI has not been issued by
	 *                                     {@link GitFileSystemImpl#toUri()} or
	 *                                     {@link GitPathImpl#toUri()}.
	 * @throws FileSystemNotFoundException If the file system, indirectly identified
	 *                                     by the URI, is not open or has not been
	 *                                     created by this provider.
	 */
	GitPathImpl getPath(URI gitFsUri);

	SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException;

	/**
	 * {@inheritDoc}
	 * <p>
	 * The elements returned by the directory stream's
	 * {@link DirectoryStream#iterator iterator} are of type {@code
	 * GitPath}.
	 *
	 * @throws IllegalArgumentException if {@code dir} cannot be cast to
	 *                                  {@link GitPathImpl}
	 */
	DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException;

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	void createDirectory(Path dir, FileAttribute<?>... attrs) throws ReadOnlyFileSystemException;

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws ReadOnlyFileSystemException;

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	void createLink(Path link, Path existing) throws ReadOnlyFileSystemException;

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	void delete(Path path) throws ReadOnlyFileSystemException;

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	boolean deleteIfExists(Path path) throws ReadOnlyFileSystemException;

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	void copy(Path source, Path target, CopyOption... options) throws ReadOnlyFileSystemException;

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	void move(Path source, Path target, CopyOption... options) throws ReadOnlyFileSystemException;

	/**
	 * At the moment, throws {@code UnsupportedOperationException}.
	 */
	boolean isSameFile(Path path, Path path2) throws IOException;

	/**
	 * At the moment, throws {@code UnsupportedOperationException}.
	 */
	boolean isHidden(Path path) throws IOException;

	/**
	 * At the moment, throws {@code UnsupportedOperationException}.
	 */
	FileStore getFileStore(Path path) throws IOException;

	/**
	 * @throws ReadOnlyFileSystemException if {@code modes} contain {@code WRITE}.
	 */
	void checkAccess(Path path, AccessMode... modes)
			throws ReadOnlyFileSystemException, AccessDeniedException, NoSuchFileException, IOException;

	/**
	 * At the moment, throws {@code UnsupportedOperationException}.
	 */
	<V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options);

	/**
	 * TODO arguably, read "link.txt" should read the link itself, when not
	 * following links.
	 */
	<A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
			throws IOException;

	/**
	 * At the moment, throws {@code UnsupportedOperationException}.
	 */
	Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException;

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	void setAttribute(Path path, String attribute, Object value, LinkOption... options)
			throws ReadOnlyFileSystemException;

	Path readSymbolicLink(Path link)
			throws IOException, NoSuchFileException, NotLinkException, AbsoluteLinkException, SecurityException;

}
