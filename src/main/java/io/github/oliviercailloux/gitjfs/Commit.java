package io.github.oliviercailloux.gitjfs;

import com.google.common.collect.ImmutableList;
import java.time.ZonedDateTime;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;

/**
 * An immutable commit, with author and committer information (name, email, date).
 * <p>
 * Note that authoring comes logically before committing but there is no guarantee that the author
 * date and committer date follow this ordering (and this class does not attempt to verify this):
 * because the dates are set by the client, manual tampering or incorrect clock may yield a
 * committer time earlier than author time. This is rare in practice, but happens. For example,
 * <a href=
 * "https://api.github.com/repos/checkstyle/checkstyle/commits/812b3416dcda571de5e6f7abd9d4e4c68c4dcdcf">this
 * commit</a> in the checkstyle project was authored at “2017-07-09T05:01:28Z” but committed (by
 * someone else) about twenty minutes earlier.
 */
public interface Commit {
  public static Commit from(ObjectId id, CommitSignature author, CommitSignature committer, ImmutableList<ObjectId> parents) {
    return new CommitImpl(id, author, committer, parents);
  }
  public ObjectId id();

  public CommitSignature author();

  public String authorName() ;

  public String authorEmail();

  public ZonedDateTime authorDate();

  public CommitSignature committer();

  public String committerName();

  public String committerEmail();

  public ZonedDateTime committerDate();

  /*
   * https://stackoverflow.com/questions/18301284
   */
  public ImmutableList<ObjectId> parents();

  /**
   * Two commits are equal iff they have equal ids, author and committer information and parents.
   */
  /*
   * We could get happy with comparing only the ids, as a random collision is extremely unlikely.
   * But non-random collisions appear to be not so unlikely
   * (https://stackoverflow.com/q/10434326), so let’s compare everything just to be sure not to
   * allow for exploits.
   */
  @Override
  public boolean equals(Object o2);

}
