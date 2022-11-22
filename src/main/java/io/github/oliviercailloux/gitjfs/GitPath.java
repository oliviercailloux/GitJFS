package io.github.oliviercailloux.gitjfs;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public interface GitPath extends Path {

	/**
	 * Returns a {@code Path} object representing the absolute path of this path.
	 *
	 * <p>
	 * If this path is already {@link Path#isAbsolute absolute} then this method
	 * simply returns this path. Otherwise, this method returns a path with a root
	 * component referring to the <tt>main</tt> branch.
	 *
	 * <p>
	 * This method does not access the underlying file system and requires no
	 * specific permission.
	 *
	 */
	@Override
	GitPath toAbsolutePath();

	@Override
	GitFileSystemImpl getFileSystem();

	/**
	 * Returns {@code true} iff this path has a root component.
	 *
	 * @return {@code true} if, and only if, this path is absolute
	 */
	@Override
	boolean isAbsolute();

	/**
	 * {@inheritDoc}
	 * <p>
	 * To obtain the root component that this path (possibly implicitly) refers to,
	 * including in the case it is relative, use {@code toAbsolutePath().getRoot()}.
	 *
	 */
	@Override
	GitPathRoot getRoot();

	@Override
	int getNameCount();

	/**
	 * @return a relative path with exactly one name element (which is empty iff
	 *         this path is the empty path)
	 */
	@Override
	GitPath getName(int index);

	@Override
	GitPath subpath(int beginIndex, int endIndex);

	/**
	 * @return a relative path representing the name of the file or directory (an
	 *         empty path iff this path is an empty path); or {@code null} if this
	 *         path has zero elements
	 */
	@Override
	GitPath getFileName();

	@Override
	GitPath getParent();

	/**
	 * Tests if this path starts with the given path.
	 *
	 * <p>
	 * This path <em>starts</em> with the given path if this path’s root component
	 * <em>equals</em> the root component of the given path, and this path starts
	 * with the same name elements as the given path. If the given path has more
	 * name elements than this path then {@code false} is returned.
	 *
	 * <p>
	 * If the given path is associated with a different {@code FileSystem} to this
	 * path then {@code false} is returned.
	 *
	 * @param other the given path
	 *
	 * @return {@code true} iff this path starts with the given path.
	 */
	@Override
	boolean startsWith(Path other);

	/**
	 * Tests if this path ends with the given path.
	 *
	 * <p>
	 * If the given path has <em>N</em> elements, and no root component, and this
	 * path has <em>N</em> or more elements, then this path ends with the given path
	 * if the last <em>N</em> elements of each path, starting at the element
	 * farthest from the root, are equal.
	 *
	 * <p>
	 * If the given path has a root component then this path ends with the given
	 * path if the root component of this path <em>equals</em> the root component of
	 * the given path, and the corresponding elements of both paths are equal.
	 *
	 * <p>
	 * If the given path is associated with a different {@code FileSystem} to this
	 * path then {@code false} is returned.
	 *
	 * @param other the given path
	 *
	 * @return {@code true} iff this path ends with the given path.
	 */
	@Override
	boolean endsWith(Path other);

	/**
	 * Returns a path that is this path with redundant name elements eliminated.
	 * <p>
	 * All occurrences of "{@code .}" are considered redundant. If a "{@code ..}" is
	 * preceded by a non-"{@code ..}" name then both names are considered redundant
	 * (the process to identify such names is repeated until it is no longer
	 * applicable).
	 * <p>
	 * This method does not access the file system; the path may not locate a file
	 * that exists. Eliminating "{@code ..}" and a preceding name from a path may
	 * result in the path that locates a different file than the original path. This
	 * can arise when the preceding name is a symbolic link.
	 *
	 * @return the resulting path or this path if it does not contain redundant name
	 *         elements; an empty path is returned if this path does not have a root
	 *         component and all name elements are redundant
	 *
	 * @see #getParent
	 */
	@Override
	GitPath normalize();

	/**
	 * Resolves the given path against this path.
	 *
	 * <p>
	 * If the {@code other} parameter is an {@link #isAbsolute() absolute} path
	 * (equivalently, if it has a root component), then this method trivially
	 * returns {@code other}. If {@code other} is an <i>empty path</i> then this
	 * method trivially returns this path. Otherwise this method considers this path
	 * to be a directory and resolves the given path against this path: this method
	 * <em>joins</em> the given path to this path and returns a resulting path that
	 * {@link #endsWith ends} with the given path.
	 * <p>
	 * This method does not access the file system.
	 *
	 * @see #relativize
	 */
	@Override
	GitPath resolve(Path other);

	/**
	 * Resolves the given path against this path.
	 *
	 * <p>
	 * If the {@code other} parameter represents an {@link #isAbsolute() absolute}
	 * path (equivalently, if it starts with a <tt>/</tt>), then this method returns
	 * the git path represented by {@code other}. If {@code other} is an empty
	 * string then this method trivially returns this path. Otherwise this method
	 * considers this path to be a directory and resolves the given path against
	 * this path: this method <em>joins</em> the given path to this path and returns
	 * a resulting path that {@link #endsWith ends} with the given path.
	 * <p>
	 * This is equivalent to converting the given path string to a {@code Path} and
	 * resolving it against this {@code Path} in the manner specified by the
	 * {@link #resolve(Path) resolve} method.
	 * <p>
	 * For example, suppose that a path represents "{@code foo/bar}", then invoking
	 * this method with the path string "{@code gus}" will result in the git path
	 * "{@code foo/bar/gus}".
	 * <p>
	 * This method does not access the file system.
	 *
	 * @see #relativize
	 */
	@Override
	GitPath resolve(String other);

	/**
	 * Constructs a relative path between this path and a given path.
	 *
	 * <p>
	 * Relativization is the inverse of {@link #resolve(Path) resolution}. This
	 * method attempts to construct a {@link #isAbsolute relative} path that when
	 * {@link #resolve(Path) resolved} against this path, yields a path that locates
	 * the same file as the given path. For example, if this path is {@code "a/b"}
	 * and the given path is {@code "a/b/c/d"} then the resulting relative path
	 * would be {@code "c/d"}. A relative path between two paths can be constructed
	 * iff the two paths both are relative, or both are absolute and have the same
	 * root component. If this path and the given path are {@link #equals equal}
	 * then an <i>empty path</i> is returned.
	 *
	 * <p>
	 * For any two {@link #normalize normalized} paths <i>p</i> and <i>q</i>, where
	 * <i>q</i> does not have a root component, <blockquote>
	 * <i>p</i>{@code .relativize(}<i>p</i>
	 * {@code .resolve(}<i>q</i>{@code )).equals(}<i>q</i>{@code )} </blockquote>
	 *
	 * <p>
	 * TODO When symbolic links are supported, then whether the resulting path, when
	 * resolved against this path, yields a path that can be used to locate the
	 * {@link Files#isSameFile same} file as {@code other} is implementation
	 * dependent. For example, if this path is {@code "/a/b"} and the given path is
	 * {@code "/a/x"} then the resulting relative path may be {@code
	 * "../x"}. If {@code "b"} is a symbolic link then is implementation dependent
	 * if {@code "a/b/../x"} would locate the same file as {@code "/a/x"}.
	 *
	 * @param other the path to relativize against this path
	 *
	 * @return the resulting relative path, or an empty path if both paths are equal
	 *
	 * @throws IllegalArgumentException if {@code other} is not a {@code Path} that
	 *                                  can be relativized against this path
	 */
	@Override
	GitPath relativize(Path other);

	/**
	 * Returns a URI referring to the git file system instance associated to this
	 * path, and referring to this specific file in that file system.
	 *
	 * @see GitFileSystemProvider#getPath(URI)
	 */
	@Override
	URI toUri();

	/**
	 * Returns the <em>real</em> path of an existing file.
	 *
	 * <p>
	 * This method derives from this path, an {@link #isAbsolute absolute} path that
	 * locates the {@link Files#isSameFile same} file as this path, but with name
	 * elements that represent the actual name of the directories and the file:
	 * symbolic links are resolved; and redundant name elements are removed.
	 *
	 * <p>
	 * The {@code options} array may be used to indicate how symbolic links are
	 * handled. By default, symbolic links are resolved to their final target, thus,
	 * the resulting path contains no symbolic links. If the option
	 * {@link LinkOption#NOFOLLOW_LINKS NOFOLLOW_LINKS} is present and the given
	 * path contains symbolic links, this method throws
	 * PathCouldNotBeFoundException.
	 *
	 * @param options options indicating how symbolic links are handled
	 *
	 * @return an absolute path that represents the same path as the file located by
	 *         this object but with no symbolic links and no special name elements
	 *         {@code .} or {@code ..}
	 *
	 * @throws IOException       if the file does not exist or an I/O error occurs,
	 *                           or (in deviation from the spec) if it can’t be
	 *                           determined whether this file exists due to the path
	 *                           containing symbolic links whereas symbolic links
	 *                           can’t be followed. I have no idea what the spec
	 *                           wants the implementor to do in such a case, apart
	 *                           perhaps from returning the original path, which
	 *                           would seem quite surprising to me; and in
	 *                           supplement, the readAttributes method is supposed
	 *                           (from Files.exists) to throw an IOException when it
	 *                           can’t be determined whether the file exists for
	 *                           that same reason.
	 * @throws SecurityException In case a relevant part of the underlying file
	 *                           system can’t be accessed
	 */
	@Override
	Path toRealPath(LinkOption... options) throws IOException, PathCouldNotBeFoundException, NoSuchFileException;

	/**
	 * Compares path according to their string form. The spec mandates
	 * “lexicographical” comparison, but I ignore what this means: apparently not
	 * that the root components must be considered first, then the rest, considering
	 * that the OpenJdk implementation of the Linux default file system sorts ""
	 * before "/" before "a"; and probably not that they must be sorted by
	 * lexicographic ordering of their string form, as it would then be a bit odd to
	 * specify that “The ordering defined by this method is provider specific”.
	 */
	@Override
	int compareTo(Path other);

	/**
	 * Tests this path for equality with the given object.
	 *
	 * <p>
	 * This method returns {@code true} iff the given object is a git path, their
	 * git file systems are equal, and the paths have equal root components (or they
	 * are both absent) and internal paths. The internal paths are compared in a
	 * case-sensitive way (conforming to the Linux concept of path equality).
	 * Equivalently, two git paths are equal iff they are associated to the same git
	 * file system and have the same {@link GitPathImpl string forms}.
	 *
	 * <p>
	 * This method does not access the file system and the files are not required to
	 * exist.
	 *
	 * @see Files#isSameFile(Path, Path) (TODO)
	 */
	@Override
	boolean equals(Object o2);

	@Override
	int hashCode();

	/**
	 * Returns the {@link GitPathImpl string form} of this path.
	 *
	 * <p>
	 * If this path was created by converting a path string using the
	 * {@link FileSystem#getPath getPath} method then the path string returned by
	 * this method may differ from the original String used to create the path.
	 *
	 */
	@Override
	String toString();

}