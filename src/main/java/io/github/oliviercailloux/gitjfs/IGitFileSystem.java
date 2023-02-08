package io.github.oliviercailloux.gitjfs;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableGraph;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemProviderImpl;
import io.github.oliviercailloux.gitjfs.impl.GitPathImpl;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Set;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;

/**
 * <p>
 * A git file system. Associated to a git repository. Can be used to obtain
 * {@link GitPathImpl} instances.
 * </p>
 * <p>
 * Must be {@link #close() closed} to release resources associated with readers.
 * </p>
 * <p>
 * Reads links transparently, as documented in <a href=
 * "https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/package-summary.html">java.nio.file</a>.
 * Thus, assuming {@code dir} is a symlink to {@code otherdir}, reading
 * {@code dir/file.txt} reads {@code otherdir/file.txt}. This is also how git
 * <a href="https://stackoverflow.com/a/954575">operates</a>: checking out
 * {@code dir} will restore it as a symlink to {@code otherdir}. Use
 * {@link GitFileSystemProviderImpl#readSymbolicLink} to obtain the target of a
 * link. This library will however refuse to follow a link out of the git
 * repository it originates from.
 * <p>
 * Note that a git repository <a href="https://stackoverflow.com/a/3731139">does
 * not use hard links</a>.
 * <p>
 * The graph (a DAG, thus, irreflexive) represents the has-as-child relation:
 * the successors of a node are its children; following the successors
 * (children) relation goes forward in time; following the predecessors
 * (parents) relation goes back in time; a pair (a, b) in the graph represents a
 * parent commit a and its child commit b.
 * <p>
 * This contradicts the usual git vision, where the edge represents the parent
 * relation, thus, flow from child commits to their parents (which reflects the
 * actual pointer locations, I suppose); but it is more intuitive that the
 * “successors” relation goes forward in time, in other words, that the edges
 * flow in the direction of time. Furthermore, in graph theory, the usual
 * convention is that the roots are the node without predecessors, and that the
 * DAG flows outwards from the roots (if the DAG is a tree, this is then called
 * an out-tree or arborescence, in Wikipedia terminology). With the convention
 * adopted here, the roots, in that sense, are also the first nodes in time,
 * which makes sense intuitively. With the git convention (where the edges flow
 * from child commits to their parents), you have to choose between calling a
 * “root” a node which has no successors but possibly predecessors, or calling a
 * “root” a node which is at the end of time, which both are counter-intuitive.
 * I suppose this discrepancy between the common edge orientation and the
 * time-flow is a usual problem with VCSes more generally (but I am not sure).
 * https://math.stackexchange.com/questions/1374802
 *
 *
 * @see #getAbsolutePath(String, String...)
 * @see #getRelativePath(String...)
 * @see #getGitRootDirectories()
 */
public interface IGitFileSystem extends AutoCloseable {

	/**
	 * Converts a path string, or a sequence of strings that when joined form a path
	 * string, to a {@code GitPath}. If {@code first} starts with {@code /} (or if
	 * {@code first} is empty and the first non-empty string in {@code more} starts
	 * with {@code /}), this method behaves as if
	 * {@link #getAbsolutePath(String, String...)} had been called. Otherwise, it
	 * behaves as if {@link #getRelativePath(String...)} had been called.
	 * <p>
	 * No check is performed to ensure that the path refers to an existing git
	 * object in this git file system.
	 * </p>
	 *
	 * @param first the path string or initial part of the path string
	 * @param more  additional strings to be joined to form the path string
	 *
	 * @return the resulting {@code Path}
	 *
	 * @throws InvalidPathException If the path string cannot be converted
	 */
	GitPath getPath(String first, String... more);

	/**
	 * <p>
	 * Converts an absolute git path string, or a sequence of strings that when
	 * joined form an absolute git path string, to an absolute {@code GitPath}.
	 * </p>
	 * <p>
	 * If {@code more} does not specify any elements then the value of the
	 * {@code first} parameter is the path string to convert.
	 * </p>
	 * <p>
	 * If {@code more} specifies one or more elements then each non-empty string,
	 * including {@code first}, is considered to be a sequence of name elements (see
	 * {@link Path}) and is joined to form a path string using {@code /} as
	 * separator. If {@code first} does not end with {@code //} (but ends with
	 * {@code /}, as required), and if {@code more} does not start with {@code /},
	 * then a {@code /} is added so that there will be two slashes joining
	 * {@code first} to {@code more}.
	 * </p>
	 * <p>
	 * For example, if {@code getAbsolutePath("/refs/heads/main/","foo","bar")} is
	 * invoked, then the path string {@code "/refs/heads/main//foo/bar"} is
	 * converted to a {@code Path}.
	 * </p>
	 * <p>
	 * No check is performed to ensure that the path refers to an existing git
	 * object in this git file system.
	 * </p>
	 *
	 * @param first the string form of the root component, possibly followed by
	 *              other path segments. Must start with <tt>/refs/</tt> or
	 *              <tt>/heads/</tt> or <tt>/tags/</tt> or be a slash followed by a
	 *              40-characters long sha-1; must contain at most once {@code //};
	 *              if does not contain {@code //}, must end with {@code /}.
	 * @param more  may start with {@code /}.
	 * @return an absolute git path.
	 * @throws InvalidPathException if {@code first} does not contain a syntaxically
	 *                              valid root component
	 * @see GitPath
	 */
	GitPath getAbsolutePath(String first, String... more) throws InvalidPathException;

	/**
	 * Returns a git path referring to a commit designated by its id. No check is
	 * performed to ensure that the commit exists.
	 *
	 * @param commitId      the commit to refer to
	 * @param internalPath1 may start with a slash.
	 * @param internalPath  may start with a slash.
	 * @return an absolute path
	 * @see GitPath
	 */
	GitPath getAbsolutePath(ObjectId commitId, String internalPath1, String... internalPath);

	/**
	 * Returns a git path referring to a commit designated by its id. No check is
	 * performed to ensure that the commit exists.
	 *
	 * @param commitId the commit to refer to
	 * @return a git path root
	 * @see GitPathRoot
	 */
	GitPathRootSha getPathRoot(ObjectId commitId);

	GitPathRootRef getPathRootRef(String rootStringForm) throws InvalidPathException;

	/**
	 * Returns an absolute git path. No check is performed to ensure that the ref
	 * exists, or that the commit this refers to exists.
	 *
	 * @param rootStringForm the string form of the root component. Must start with
	 *                       <tt>/refs/</tt> or <tt>/heads/</tt> or <tt>/tags/</tt>
	 *                       or be a 40-characters long sha-1 surrounded by slash
	 *                       characters; must end with <tt>/</tt>; may not contain
	 *                       <tt>//</tt> nor <tt>\</tt>.
	 * @return a git path root
	 * @throws InvalidPathException if {@code rootStringForm} does not contain a
	 *                              syntaxically valid root component
	 * @see GitPathRoot
	 */
	GitPathRoot getPathRoot(String rootStringForm) throws InvalidPathException;

	/**
	 * <p>
	 * Converts a relative git path string, or a sequence of strings that when
	 * joined form a relative git path string, to a relative {@code GitPath}.
	 * </p>
	 * <p>
	 * Each non-empty string in {@code names} is considered to be a sequence of name
	 * elements (see {@link Path}) and is joined to form a path string using
	 * {@code /} as separator.
	 * </p>
	 * <p>
	 * For example, if {@code getRelativePath("foo","bar")} is invoked, then the
	 * path string {@code "foo/bar"} is converted to a {@code Path}.
	 * </p>
	 * <p>
	 * An <em>empty</em> path is returned iff names contain only empty strings. It
	 * then implicitly refers to the main branch of this git file system.
	 * </p>
	 * <p>
	 * No check is performed to ensure that the path refers to an existing git
	 * object in this git file system.
	 * </p>
	 *
	 * @param names the internal path; its first element (if any) may not start with
	 *              {@code /}.
	 * @return a relative git path.
	 * @throws InvalidPathException if the first non-empty string in {@code names}
	 *                              start with {@code /}.
	 * @see GitPath
	 */
	GitPath getRelativePath(String... names) throws InvalidPathException;

	/**
	 * TODO
	 */
	Iterable<FileStore> getFileStores();

	/**
	 * Retrieve the set of all commits of this repository. Consider calling rather
	 * {@code {@link #getCommitsGraph()}.getNodes()}, whose type is more precise.
	 *
	 * @return absolute path roots referring to commit ids.
	 * @throws UncheckedIOException if an I/O error occurs (I have no idea why the
	 *                              Java Files API does not want an IOException
	 *                              here)
	 */
	ImmutableSet<Path> getRootDirectories();

	/**
	 * Retrieve the set of all commits of this repository reachable from some ref.
	 * The set of nodes in the graph equals the one returned by
	 * {@link #getRootDirectories()}.
	 *
	 * @return absolute path roots, all referring to commit ids (no ref).
	 * @throws IOException if an I/O error occurs
	 */
	ImmutableGraph<GitPathRootSha> graph() throws IOException;

	/**
	 * Returns a set containing one git path root for each git ref (of the form
	 * <tt>/refs/…</tt>) contained in this git file system. This does not consider
	 * HEAD or other special references, but considers both branches and tags.
	 *
	 * @return git path roots referencing git refs (not commit ids).
	 *
	 * @throws IOException if an I/O error occurs
	 */
	ImmutableSet<GitPathRootRef> refs() throws IOException;

	/**
	 * Returns a {@code PathMatcher} that performs match operations on the
	 * {@code String} representation of {@link Path} objects by interpreting a given
	 * pattern.
	 *
	 * The {@code syntaxAndPattern} parameter identifies the syntax and the pattern
	 * and takes the form: <blockquote>
	 *
	 * <pre>
	 * <i>syntax</i><b>:</b><i>pattern</i>
	 * </pre>
	 *
	 * </blockquote> where {@code ':'} stands for itself.
	 *
	 * <p>
	 * A {@code FileSystem} implementation supports the "{@code glob}" and
	 * "{@code regex}" syntaxes, and may support others. The value of the syntax
	 * component is compared without regard to case.
	 *
	 * <p>
	 * When the syntax is "{@code glob}" then the {@code String} representation of
	 * the path is matched using a limited pattern language that resembles regular
	 * expressions but with a simpler syntax. For example:
	 *
	 * <table class="striped" style="text-align:left; margin-left:2em">
	 * <caption style="display:none">Pattern Language</caption> <thead>
	 * <tr>
	 * <th scope="col">Example
	 * <th scope="col">Description
	 * </tr>
	 * </thead> <tbody>
	 * <tr>
	 * <th scope="row">{@code *.java}</th>
	 * <td>Matches a path that represents a file name ending in {@code .java}</td>
	 * </tr>
	 * <tr>
	 * <th scope="row">{@code *.*}</th>
	 * <td>Matches file names containing a dot</td>
	 * </tr>
	 * <tr>
	 * <th scope="row">{@code *.{java,class}}</th>
	 * <td>Matches file names ending with {@code .java} or {@code .class}</td>
	 * </tr>
	 * <tr>
	 * <th scope="row">{@code foo.?}</th>
	 * <td>Matches file names starting with {@code foo.} and a single character
	 * extension</td>
	 * </tr>
	 * <tr>
	 * <th scope="row"><code>&#47;home&#47;*&#47;*</code>
	 * <td>Matches <code>&#47;home&#47;gus&#47;data</code> on UNIX platforms</td>
	 * </tr>
	 * <tr>
	 * <th scope="row"><code>&#47;home&#47;**</code>
	 * <td>Matches <code>&#47;home&#47;gus</code> and
	 * <code>&#47;home&#47;gus&#47;data</code> on UNIX platforms</td>
	 * </tr>
	 * <tr>
	 * <th scope="row"><code>C:&#92;&#92;*</code>
	 * <td>Matches <code>C:&#92;foo</code> and <code>C:&#92;bar</code> on the
	 * Windows platform (note that the backslash is escaped; as a string literal in
	 * the Java Language the pattern would be
	 * <code>"C:&#92;&#92;&#92;&#92;*"</code>)</td>
	 * </tr>
	 * </tbody>
	 * </table>
	 *
	 * <p>
	 * The following rules are used to interpret glob patterns:
	 *
	 * <ul>
	 * <li>
	 * <p>
	 * The {@code *} character matches zero or more {@link Character characters} of
	 * a {@link Path#getName(int) name} component without crossing directory
	 * boundaries.
	 * </p>
	 * </li>
	 *
	 * <li>
	 * <p>
	 * The {@code **} characters matches zero or more {@link Character characters}
	 * crossing directory boundaries.
	 * </p>
	 * </li>
	 *
	 * <li>
	 * <p>
	 * The {@code ?} character matches exactly one character of a name component.
	 * </p>
	 * </li>
	 *
	 * <li>
	 * <p>
	 * The backslash character ({@code \}) is used to escape characters that would
	 * otherwise be interpreted as special characters. The expression {@code \\}
	 * matches a single backslash and "\{" matches a left brace for example.
	 * </p>
	 * </li>
	 *
	 * <li>
	 * <p>
	 * The {@code [ ]} characters are a <i>bracket expression</i> that match a
	 * single character of a name component out of a set of characters. For example,
	 * {@code [abc]} matches {@code "a"}, {@code "b"}, or {@code "c"}. The hyphen
	 * ({@code -}) may be used to specify a range so {@code [a-z]} specifies a range
	 * that matches from {@code "a"} to {@code "z"} (inclusive). These forms can be
	 * mixed so [abce-g] matches {@code "a"}, {@code "b"}, {@code "c"}, {@code "e"},
	 * {@code "f"} or {@code "g"}. If the character after the {@code [} is a
	 * {@code !} then it is used for negation so {@code
	 *   [!a-c]} matches any character except {@code "a"}, {@code "b"}, or {@code
	 *   "c"}.
	 * <p>
	 * Within a bracket expression the {@code *}, {@code ?} and {@code \} characters
	 * match themselves. The ({@code -}) character matches itself if it is the first
	 * character within the brackets, or the first character after the {@code !} if
	 * negating.
	 * </p>
	 * </li>
	 *
	 * <li>
	 * <p>
	 * The {@code { }} characters are a group of subpatterns, where the group
	 * matches if any subpattern in the group matches. The {@code ","} character is
	 * used to separate the subpatterns. Groups cannot be nested.
	 * </p>
	 * </li>
	 *
	 * <li>
	 * <p>
	 * Leading period<code>&#47;</code>dot characters in file name are treated as
	 * regular characters in match operations. For example, the {@code "*"} glob
	 * pattern matches file name {@code ".login"}. The {@link Files#isHidden} method
	 * may be used to test whether a file is considered hidden.
	 * </p>
	 * </li>
	 *
	 * <li>
	 * <p>
	 * All other characters match themselves in an implementation dependent manner.
	 * This includes characters representing any {@link FileSystem#getSeparator
	 * name-separators}.
	 * </p>
	 * </li>
	 *
	 * <li>
	 * <p>
	 * The matching of {@link Path#getRoot root} components is highly
	 * implementation-dependent and is not specified.
	 * </p>
	 * </li>
	 *
	 * </ul>
	 *
	 * <p>
	 * When the syntax is "{@code regex}" then the pattern component is a regular
	 * expression as defined by the {@link java.util.regex.Pattern} class.
	 *
	 * <p>
	 * For both the glob and regex syntaxes, the matching details, such as whether
	 * the matching is case sensitive, are implementation-dependent and therefore
	 * not specified.
	 *
	 * @param syntaxAndPattern The syntax and pattern
	 *
	 * @return A path matcher that may be used to match paths against the pattern
	 *
	 * @throws IllegalArgumentException               If the parameter does not take
	 *                                                the form:
	 *                                                {@code syntax:pattern}
	 * @throws java.util.regex.PatternSyntaxException If the pattern is invalid
	 * @throws UnsupportedOperationException          If the pattern syntax is not
	 *                                                known to the implementation
	 *
	 * @see Files#newDirectoryStream(Path,String)
	 */
	PathMatcher getPathMatcher(String syntaxAndPattern);

	/**
	 * Returns the name separator, represented as a string.
	 *
	 * <p>
	 * The name separator is used to separate names in a path string. An
	 * implementation may support multiple name separators in which case this method
	 * returns an implementation specific <em>default</em> name separator. This
	 * separator is used when creating path strings by invoking the
	 * {@link Path#toString() toString()} method.
	 *
	 * <p>
	 * In the case of the default provider, this method returns the same separator
	 * as {@link java.io.File#separator}.
	 *
	 * @return The name separator
	 */
	String getSeparator();

	/**
	 * Returns the {@code UserPrincipalLookupService} for this file system
	 * <i>(optional operation)</i>. The resulting lookup service may be used to
	 * lookup user or group names.
	 *
	 * <p>
	 * <b>Usage Example:</b> Suppose we want to make "joe" the owner of a file:
	 *
	 * <pre>
	 * UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
	 * Files.setOwner(path, lookupService.lookupPrincipalByName("joe"));
	 * </pre>
	 *
	 * @throws UnsupportedOperationException If this {@code FileSystem} does not
	 *                                       does have a lookup service
	 *
	 * @return The {@code UserPrincipalLookupService} for this file system
	 */
	UserPrincipalLookupService getUserPrincipalLookupService();

	/**
	 * Tells whether or not this file system is open.
	 *
	 * <p>
	 * File systems created by the default provider are always open.
	 *
	 * @return {@code true} if, and only if, this file system is open
	 */
	boolean isOpen();

	/**
	 * Tells whether or not this file system allows only read-only access to its
	 * file stores.
	 *
	 * @return {@code true} if, and only if, this file system provides read-only
	 *         access
	 */
	boolean isReadOnly();

	/**
	 * Constructs a new {@link WatchService} <i>(optional operation)</i>.
	 *
	 * <p>
	 * This method constructs a new watch service that may be used to watch
	 * registered objects for changes and events.
	 *
	 * @return a new watch service
	 *
	 * @throws UnsupportedOperationException If this {@code FileSystem} does not
	 *                                       support watching file system objects
	 *                                       for changes and events. This exception
	 *                                       is not thrown by {@code FileSystems}
	 *                                       created by the default provider.
	 * @throws IOException                   If an I/O error occurs
	 */
	WatchService newWatchService() throws IOException;

	/**
	 * Returns the provider that created this file system.
	 *
	 * @return The provider that created this file system.
	 */
	GitFileSystemProvider provider();

	/**
	 * Returns the set of the {@link FileAttributeView#name names} of the file
	 * attribute views supported by this {@code FileSystem}.
	 *
	 * <p>
	 * The {@link BasicFileAttributeView} is required to be supported and therefore
	 * the set contains at least one element, "basic".
	 *
	 * <p>
	 * The {@link FileStore#supportsFileAttributeView(String)
	 * supportsFileAttributeView(String)} method may be used to test if an
	 * underlying {@link FileStore} supports the file attributes identified by a
	 * file attribute view.
	 *
	 * @return An unmodifiable set of the names of the supported file attribute
	 *         views
	 */
	Set<String> supportedFileAttributeViews();

	public ImmutableSet<DiffEntry> getDiff(GitPathRoot first, GitPathRoot second) throws IOException;

	/**
	 * Closes this file system.
	 *
	 * <p>
	 * After a file system is closed then all subsequent access to the file system,
	 * either by methods defined by this class or on objects associated with this
	 * file system, throw {@link ClosedFileSystemException}. If the file system is
	 * already closed then invoking this method has no effect.
	 *
	 * <p>
	 * Closing a file system will close all open {@link java.nio.channels.Channel
	 * channels}, {@link DirectoryStream directory-streams}, {@link WatchService
	 * watch-service}, and other closeable objects associated with this file system.
	 * The {@link FileSystems#getDefault default} file system cannot be closed.
	 *
	 * @throws IOException                   If an I/O error occurs
	 * @throws UnsupportedOperationException Thrown in the case of the default file
	 *                                       system
	 */
	@Override
	void close() throws IOException;

	/**
	 * <p>
	 * Returns a gitjfs URI that identifies this git file system, and this specific
	 * git file system instance while it is open.
	 * </p>
	 * <p>
	 * While this instance is open, giving the returned URI to
	 * {@link GitFileSystemProviderImpl#getFileSystem(URI)} will return this file
	 * system instance; giving it to {@link GitFileSystemProviderImpl#getPath(URI)}
	 * will return the default path associated to this file system.
	 * </p>
	 *
	 * @return the URI that identifies this file system.
	 */
	URI toUri();
}
