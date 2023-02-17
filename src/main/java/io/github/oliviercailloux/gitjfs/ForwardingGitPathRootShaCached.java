package io.github.oliviercailloux.gitjfs;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.ObjectId;

public abstract class ForwardingGitPathRootShaCached implements GitPathRootShaCached {
  protected abstract GitPathRootShaCached delegate();

  @Override
  public GitFileSystem getFileSystem() {
    return delegate().getFileSystem();
  }

  @Deprecated
  @Override
  public boolean isAbsolute() {
    return delegate().isAbsolute();
  }

  @Deprecated
  @Override
  public GitPathRoot toAbsolutePath() {
    return delegate().toAbsolutePath();
  }

  @Deprecated
  @Override
  public GitPathRoot getRoot() {
    return delegate().getRoot();
  }

  @Deprecated
  @Override
  public GitPath getParent() {
    return delegate().getParent();
  }

  @Deprecated
  @Override
  public GitPathRootSha toSha() {
    return delegate().toSha();
  }

  @Deprecated
  @Override
  public GitPathRootShaCached toShaCached() {
    return delegate().toShaCached();
  }

  @Deprecated
  @Override
  public boolean isCommitId() {
    return delegate().isCommitId();
  }

  @Override
  public ObjectId getStaticCommitId() {
    return delegate().getStaticCommitId();
  }

  @Deprecated
  @Override
  public boolean isRef() {
    return delegate().isRef();
  }

  @Deprecated
  @Override
  public String getGitRef() throws IllegalStateException {
    return delegate().getGitRef();
  }

  @Override
  public Commit getCommit() {
    return delegate().getCommit();
  }

  @Override
  public ImmutableList<? extends GitPathRootSha> getParentCommits()
      throws IOException, NoSuchFileException {
    return delegate().getParentCommits();
  }

  @Override
  public int getNameCount() {
    return delegate().getNameCount();
  }

  @Override
  public GitPath getName(int index) {
    return delegate().getName(index);
  }

  @Override
  public GitPath subpath(int beginIndex, int endIndex) {
    return delegate().subpath(beginIndex, endIndex);
  }

  @Override
  public GitPath getFileName() {
    return delegate().getFileName();
  }

  @Override
  public boolean startsWith(Path other) {
    return delegate().startsWith(other);
  }

  @Override
  public boolean endsWith(Path other) {
    return delegate().endsWith(other);
  }

  @Override
  public GitPath normalize() {
    return delegate().normalize();
  }

  @Override
  public GitPath resolve(Path other) {
    return delegate().resolve(other);
  }

  @Override
  public GitPath resolve(String other) {
    return delegate().resolve(other);
  }

  @Override
  public GitPath relativize(Path other) {
    return delegate().relativize(other);
  }

  @Override
  public URI toUri() {
    return delegate().toUri();
  }

  @Override
  public GitPath toRealPath(LinkOption... options)
      throws IOException, PathCouldNotBeFoundException, NoSuchFileException {
    return delegate().toRealPath(options);
  }

  @Override
  public boolean endsWith(String other) {
    return delegate().endsWith(other);
  }

  @Override
  public int compareTo(Path other) {
    return delegate().compareTo(other);
  }

  @Override
  public boolean equals(Object o2) {
    return delegate().equals(o2);
  }

  @Override
  public int hashCode() {
    return delegate().hashCode();
  }

  @Override
  public String toString() {
    return delegate().toString();
  }

  @Override
  public Iterator<Path> iterator() {
    return delegate().iterator();
  }

  @Override
  public Spliterator<Path> spliterator() {
    return delegate().spliterator();
  }

  @Override
  public void forEach(Consumer<? super Path> action) {
    delegate().forEach(action);
  }

  @Override
  public boolean startsWith(String other) {
    return delegate().startsWith(other);
  }

  @Override
  public Path resolveSibling(Path other) {
    return delegate().resolveSibling(other);
  }

  @Override
  public Path resolveSibling(String other) {
    return delegate().resolveSibling(other);
  }

  @Override
  public File toFile() {
    return delegate().toFile();
  }

  @Override
  public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers)
      throws IOException {
    return delegate().register(watcher, events, modifiers);
  }

  @Override
  public WatchKey register(WatchService watcher, Kind<?>... events) throws IOException {
    return delegate().register(watcher, events);
  }
}
