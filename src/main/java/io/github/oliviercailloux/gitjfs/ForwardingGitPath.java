package io.github.oliviercailloux.gitjfs;

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

public abstract class ForwardingGitPath implements GitPath {
  public static boolean defaultEquals(GitPath p1, Object o2) {
    if (!(o2 instanceof GitPath)) {
      return false;
    }
    final GitPath p2 = (GitPath) o2;
    return p1.getFileSystem().equals(p2.getFileSystem()) && p1.toString().equals(p2.toString());
  }

  protected abstract GitPath delegate();

  @Override
  public GitFileSystem getFileSystem() {
    return delegate().getFileSystem();
  }

  @Override
  public boolean isAbsolute() {
    return delegate().isAbsolute();
  }

  @Override
  public GitPath toAbsolutePath() {
    return delegate().toAbsolutePath();
  }

  @Override
  public GitPathRoot getRoot() {
    return delegate().getRoot();
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
  public GitPath getParent() {
    return delegate().getParent();
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
  public boolean endsWith(String other) {
    return delegate().endsWith(other);
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
