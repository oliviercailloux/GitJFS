package io.github.oliviercailloux.gitjfs;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import org.eclipse.jgit.lib.ObjectId;

/**
 * An immutable commit, with author and committer information (name, email, date).
 * <p>
 * Note that authoring comes logically before committing but there is no guarantee that the author
 * date and committer date follow this ordering (and this class does not attempt to verify this):
 * because the dates are set by the client, manual tampering or incorrect clock may cause author
 * time to be after committer time. This is rare in practice, but happens. For example, <a href=
 * "https://api.github.com/repos/checkstyle/checkstyle/commits/812b3416dcda571de5e6f7abd9d4e4c68c4dcdcf">this
 * commit</a> in the checkstyle project was authored at “2017-07-09T05:01:28Z” but committed (by
 * someone else) about twenty minutes earlier.
 */
public class Commit {
  public static Commit from(ObjectId id, String authorName, String authorEmail,
      ZonedDateTime authorDate, String committerName, String committerEmail,
      ZonedDateTime committerDate, List<ObjectId> parents) {
    return new Commit(id, authorName, authorEmail, authorDate, committerName, committerEmail,
        committerDate, parents);
  }

  static Commit create(ObjectId id, String authorName, String authorEmail, ZonedDateTime authorDate,
      String committerName, String committerEmail, ZonedDateTime committerDate,
      List<ObjectId> parents) {
    return new Commit(id, authorName, authorEmail, authorDate, committerName, committerEmail,
        committerDate, parents);
  }

  private final ObjectId id;
  private final String authorName;
  private final String authorEmail;
  private final ZonedDateTime authorDate;
  private final String committerName;
  private final String committerEmail;
  private final ZonedDateTime committerDate;
  /**
   * https://stackoverflow.com/questions/18301284
   */
  private final ImmutableList<ObjectId> parents;

  private Commit(ObjectId id, String authorName, String authorEmail, ZonedDateTime authorDate,
      String committerName, String committerEmail, ZonedDateTime committerDate,
      List<ObjectId> parents) {
    this.id = checkNotNull(id);
    this.authorName = checkNotNull(authorName);
    this.authorEmail = checkNotNull(authorEmail);
    this.authorDate = checkNotNull(authorDate);
    this.committerName = checkNotNull(committerName);
    this.committerEmail = checkNotNull(committerEmail);
    this.committerDate = checkNotNull(committerDate);
    this.parents = ImmutableList.copyOf(parents);
  }

  public ObjectId id() {
    return id;
  }

  public String authorName() {
    return authorName;
  }

  public String authorEmail() {
    return authorEmail;
  }

  public ZonedDateTime authorDate() {
    return authorDate;
  }

  public String committerName() {
    return committerName;
  }

  public String committerEmail() {
    return committerEmail;
  }

  public ZonedDateTime committerDate() {
    return committerDate;
  }

  public ImmutableList<ObjectId> parents() {
    return parents;
  }

  /**
   * Two commits are equal iff they have equal ids, author and committer information and parents.
   */
  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof Commit)) {
      return false;
    }
    /*
     * We could get happy with comparing only the ids, as a random collision is extremely unlikely.
     * But non-random collisions appear to be not so unlikely
     * (https://stackoverflow.com/q/10434326), so let’s compare everything just to be sure not to
     * allow for exploits.
     */
    final Commit c2 = (Commit) o2;
    return id.equals(c2.id) && authorName.equals(c2.authorName)
        && authorEmail.equals(c2.authorEmail) && authorDate.equals(c2.authorDate)
        && committerName.equals(c2.committerName) && committerEmail.equals(c2.committerEmail)
        && committerDate.equals(c2.committerDate) && parents.equals(c2.parents);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, authorName, authorEmail, authorDate, committerName, committerEmail,
        committerDate, parents);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("id", id).add("authorName", authorName)
        .add("authorDate", authorDate).add("committerName", committerName)
        .add("committerDate", committerDate).add("parents", parents).toString();
  }
}
