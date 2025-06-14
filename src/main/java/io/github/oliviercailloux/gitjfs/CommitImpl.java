package io.github.oliviercailloux.gitjfs;

import com.google.common.collect.ImmutableList;
import java.time.ZonedDateTime;
import org.eclipse.jgit.lib.ObjectId;

public record CommitImpl (ObjectId id, CommitSignature author, CommitSignature committer,
    ImmutableList<ObjectId> parents) implements Commit {

  @Override
  public String authorName() {
    return author.name();
  }

  @Override
  public String authorEmail() {
    return author.email();
  }

  @Override
  public ZonedDateTime authorDate() {
    return author.date();
  }

  @Override
  public String committerName() {
    return committer.name();
  }

  @Override
  public String committerEmail() {
    return committer.email();
  }

  @Override
  public ZonedDateTime committerDate() {
    return committer.date();
  }
}
