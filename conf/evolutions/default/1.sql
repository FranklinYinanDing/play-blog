# --- !Ups

CREATE TABLE Member (
  ID           INTEGER PRIMARY KEY,
  Name         VARYING CHARACTER(20) NOT NULL UNIQUE
    ON CONFLICT IGNORE,
  PasswordSalt CHARACTER(64)         NOT NULL UNIQUE
    ON CONFLICT IGNORE,
  PasswordHash CHARACTER(64)         NOT NULL
);

CREATE TABLE Blog (
  ID           INTEGER PRIMARY KEY,
  Path         VARYING CHARACTER(255) NOT NULL,
  Title        VARYING CHARACTER(255) NOT NULL,
  Content      TEXT,
  CreatedYear  INTEGER                NOT NULL,
  CreatedMonth INTEGER                NOT NULL,
  CreatedDay   INTEGER                NOT NULL,
  AuthorID     INTEGER                NOT NULL,
  UNIQUE (Path, CreatedYear, CreatedMonth, CreatedDay, AuthorID)
    ON CONFLICT IGNORE,
  UNIQUE (Title, CreatedYear, CreatedMonth, CreatedDay, AuthorID)
    ON CONFLICT IGNORE,
  FOREIGN KEY (AuthorID) REFERENCES Member (ID)
);

CREATE TABLE Tag (
  ID   INTEGER PRIMARY KEY,
  Name VARYING CHARACTER(20) NOT NULL UNIQUE
    ON CONFLICT IGNORE
);

CREATE TABLE BlogTag (
  ID     INTEGER PRIMARY KEY,
  BlogID INTEGER NOT NULL,
  TagID  INTEGER NOT NULL,
  UNIQUE (BlogID, TagID)
    ON CONFLICT IGNORE,
  FOREIGN KEY (BlogID) REFERENCES Blog (ID),
  FOREIGN KEY (TagID) REFERENCES Tag (ID)
);

# --- !Downs

DROP TABLE BlogTag;

DROP TABLE Tag;

DROP TABLE Blog;

DROP TABLE Member;
