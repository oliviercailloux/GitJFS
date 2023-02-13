package io.github.oliviercailloux.gitjfs;

import io.github.oliviercailloux.gitjfs.impl.GitFileSystemProviderImpl;
import java.net.URI;
import java.nio.file.Path;
import org.eclipse.jgit.internal.storage.file.FileRepository;

/**
 * A git file system that obtains its commit data by reading from a git directory.
 *
 * @see IGitFileSystemProvider#newFileSystemFromGitDir(Path)
 * @see IGitFileSystemProvider#newFileSystemFromFileRepository(FileRepository)
 */
public interface IGitFileFileSystem extends IGitFileSystem {

  /**
   * <p>
   * Returns a gitjfs URI that identifies this git file system by referring to the git directory
   * that it obtains its data from. The returned URI also identifies this specific git file system
   * instance, while it is open.
   * </p>
   * <p>
   * While this file system is open, the returned URI can be given to
   * {@link GitFileSystemProviderImpl#getFileSystem(URI)} to obtain this file system instance back;
   * or to {@link GitFileSystemProviderImpl#getPath(URI)} to obtain the default path associated to
   * this file system. It can also be given to {@link GitFileSystemProviderImpl#newFileSystem(URI)}
   * to obtain a new file system that obtains its data by reading from the same git directory, in a
   * new VM instance or after this one has been closed. (This identifier should not however be
   * considered stable accross releases of this library. Please open an issue if this creates a
   * problem.)
   * </p>
   *
   * @return the URI that identifies this git file system.
   */
  @Override
  URI toUri();

  /**
   * Returns the git directory this file system reads its data from. This is typically, but not
   * necessarily, a directory named “.git”.
   */
  Path getGitDir();
}
