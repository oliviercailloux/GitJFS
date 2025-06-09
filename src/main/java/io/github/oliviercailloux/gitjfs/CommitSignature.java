package io.github.oliviercailloux.gitjfs;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.eclipse.jgit.lib.PersonIdent;

public interface CommitSignature {
  public static CommitSignature from(String name, String email, ZonedDateTime date) {
    return new CommitSignatureImpl(name, email, date);
  }

  public static CommitSignature from(PersonIdent personIdent) {
    return new CommitSignatureImpl(personIdent.getName(), personIdent.getEmailAddress(),
        getCreationTime(personIdent));
  }

  private static ZonedDateTime getCreationTime(PersonIdent ident) {
    final Instant creationInstant = ident.getWhenAsInstant();
    final ZoneId creationZone = ident.getZoneId();
    final ZonedDateTime creationTime = ZonedDateTime.ofInstant(creationInstant, creationZone);
    return creationTime;
  }

  public String name();

  public String email();

  public ZonedDateTime date();
}
