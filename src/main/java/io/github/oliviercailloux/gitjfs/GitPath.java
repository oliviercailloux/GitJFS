package io.github.oliviercailloux.gitjfs;

import io.github.oliviercailloux.gitjfs.impl.GitFileSystemProviderImpl;
import io.github.oliviercailloux.gitjfs.impl.GitPathImpl;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

/**
 * A git path has an optional root component and a (possibly empty) sequence of
 * names (strings). It is also associated to a git file system.
 *
 * <h1>Corresponding commit</h1>
 * <p>
 * The root component, if it is present, represents a commit. It consists in
 * either a git reference (a string which must start with <tt>refs/</tt>, such
 * as <tt>refs/heads/main</tt>) or a commit id (represented as an
 * {@link ObjectId}).
 * <p>
 * This path is absolute iff the root component is present.
 * <p>
 * Relative paths implicitly refer to the branch <tt>main</tt>.
 * </p>
 *
 * <h1>Internal path</h1>
 * <p>
 * The sequence of names represents a path inside a given commit. Each name is a
 * string that does not contain any slash. Names are not empty, except possibly
 * the first name element when it is alone (in the special case of an empty
 * path). If a git path has no root component, then its sequence of names is non
 * empty.
 * <p>
 * A git path is <em>empty</em> iff it has no root component and a single name
 * element which is the empty string. An empty path implicitly refers to the
 * root of the branch <tt>main</tt>.
 * </p>
 *
 * <h1>String form</h1>
 * <p>
 * The string form of a path consists in the string form of its root component,
 * if it has one, followed by its internal path.
 * </p>
 * <ul>
 * <li>The string form of a root component is <tt>/gitref/</tt>, where
 * <tt>gitref</tt> is a git reference; or <tt>/sha1/</tt>, where <tt>sha1</tt>
 * is a commit id.</li>
 * <li>Its internal path is a string that starts with a slash iff the path is
 * absolute, and is composed of the names that constitute its sequence of names,
 * separated by slashes.</li>
 * </ul>
 *
 * <h1>Possible cases</h1>
 * <p>
 * It follows from these rules that an instance of this class matches exactly
 * one of these two cases (each admitting a special case).
 * </p>
 * <ul>
 * <li>It has no root component. Equivalently, its string form contains no
 * leading slash. Equivalently, its string form contains no two consecutive
 * slashes. Equivalently, it is a relative path. It implies that its sequence of
 * names is not empty. An example of string form is {@code "some/path"}.</li>
 * <ul>
 * <li>As a special case, its sequence of names may consist in a unique empty
 * name. Equivalently, it is an empty path. Equivalently, its string form is
 * {@code ""}. Such a path implicitly refers to the branch <tt>main</tt> and the
 * root directory in that branch.</li>
 * </ul>
 * <li>It has a root component. Equivalently, its string form contains a leading
 * slash. Equivalently, its string form contains two consecutive slashes exactly
 * once. Equivalently, it is an absolute path. Implies that its sequence of
 * names contain no empty name. An example of string form is
 * {@code "/refs/heads/main//some/path"}.</li>
 * <ul>
 * <li>As a special case, it may consist in a root component only, in other
 * words, have an empty sequence of names. Equivalently, its string form ends
 * with two slashes. An example of string form is
 * {@code "/refs/heads/main//"}.</li>
 * </ul>
 * </ul>
 *
 * <h1>Extended discussion</h1>
 * <p>
 * The string form of the root component starts and ends with a slash, and
 * contains more slashes iff it is a git reference. It has typically the form
 * <tt>/refs/category/someref/</tt>, where <tt>category</tt> is <tt>tags</tt>,
 * <tt>heads</tt> or <tt>remotes</tt>, but may have
 * <a href="https://git-scm.com/docs/git-check-ref-format">other</a>
 * <a href="https://stackoverflow.com/a/47208574/">forms</a>. This class
 * requires that the git reference starts with <tt>refs/</tt>, does not end with
 * <tt>/</tt> and does not contain <tt>//</tt> or <tt>\</tt> (git also imposes
 * these restrictions on git references).
 * </p>
 * <h2>Rationale</h2>
 * <p>
 * The special git reference <tt>HEAD</tt> is not accepted for simplification:
 * <tt>HEAD</tt> may be a reference to a reference, which introduces annoying
 * exceptions. E.g. that a GitPathRoot is either a ref (a pointer to a commit)
 * starting with refs/ or a commit id would not be true any more if it could
 * also be <tt>HEAD</tt>. Another, weaker, reason for refusing <tt>HEAD</tt> is
 * that it makes sense mainly with respect to a
 * <a href="https://git-scm.com/docs/gitglossary#def_working_tree">worktree</a>,
 * whereas this library is designed to read directly from the git directory,
 * independently of what happens in the worktree. This prevents, for example,
 * deciding that the default path of a git file system is the ref or commit
 * pointed by <tt>HEAD</tt>. This reason is weaker because bare repositories
 * <a href="https://stackoverflow.com/q/3301956/859604">use HEAD</a> to indicate
 * their default branch, showing that HEAD is not solely for use within a
 * worktree.
 * <p>
 * A {@link Ref} is not accepted as an input because a {@code Ref} has an object
 * id which does not update, whereas this object considers a git reference as
 * referring to commit ids dynamically. Also, {@code Ref} is more general than
 * what is called here a git ref.
 * <p>
 * The fact that the path <tt>/somegitref//</tt> is considered as a root
 * component only, thus with an empty sequence of names, can appear surprising.
 * Note first that slash is a path separator, thus cannot be part of a name. The
 * only other possible choice is thus to consider that <tt>/someref//</tt>
 * contains a sequence of name of one element, being the empty string. An
 * advantage of this choice is that the sequence of names would be never empty
 * (thereby easing usage) and that it feels like a natural generalization of the
 * case of <tt>""</tt>, a path also containing one element being the empty
 * string. But from the wording of {@link Path#getFileName()}, it seems like
 * that method should return null iff the path is a root component only, and
 * that what it should return is distinct from the root component. Note that the
 * Windows implementation <a href=
 * "https://github.com/openjdk/jdk/tree/450452bb8cb617682a3eb28ae651cb829a45dcc6/test/jdk/java/nio/file/Path/PathOps.java#L290">treats</a>
 * C:\ as a root component only, and returns {@code null} on
 * {@code getFileName()}. (And I believe that also under Windows, <tt>\</tt> is
 * considered a root component only, equivalently, an empty sequence of names.)
 * For sure, under Linux, <tt>/</tt> is a root component only. Thus, to behave
 * similarly, we have to return {@code null} to {@code getFileName()} on the
 * path <tt>/someref//</tt>, hence, to treat it as a root component only. (This
 * choice would also break the nice fact that the internal path in a git path
 * behaves like the sequence of names in a linux path, as <tt>/</tt> under Linux
 * is an empty sequence.)
 * <p>
 * The Java FS API requires that relative paths be {@link Path#toAbsolutePath()
 * automatically convertible} to absolute paths. This is arguably not very
 * natural for git, as a git repository has no concept of a default branch. To
 * comply with the requirement I have opted for the (probably) most commonly
 * used first branch name, namely, {@code main}. Alternatively I could have
 * looked automatically for the ref <a href=
 * "https://superuser.com/questions/1718677#comment2652531_1718677">/refs/remotes/origin/</a>
 * but this only introduces more complexity and requires another arguable
 * choice, as someone’s remote may be named differently than {@code origin}.
 * Whenever you need to convert a relative path to an absolute path, I advice to
 * use an explicit absolute path (using explicity the branch {@code main} if
 * desired) and resolve your relative path against this explicit absolute path.
 */
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
	GitFileSystem getFileSystem();

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
	 * @see GitFileSystemProviderImpl#getPath(URI)
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