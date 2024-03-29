PRAGMA foreign_keys=OFF;
BEGIN TRANSACTION;
CREATE TABLE `Artists` (
	`id`	INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	`name`	TEXT NOT NULL,
	`birth`	TEXT NOT NULL,
	`description`	TEXT
);
INSERT INTO Artists VALUES(1,'artist1','01-01-1989',NULL);
INSERT INTO Artists VALUES(2,'artist2','02-08-1997',NULL);
INSERT INTO Artists VALUES(3,'artist3','27-09-1997',NULL);
CREATE TABLE IF NOT EXISTS "Music" (
	`id`	INTEGER NOT NULL,
	`name`	TEXT NOT NULL,
	`year`	INTEGER NOT NULL,
	`album`	INTEGER,
	`artist`	INTEGER,
	PRIMARY KEY(`id`),
	FOREIGN KEY(`artist`) REFERENCES `Artists`(`id`),
	FOREIGN KEY(`album`) REFERENCES `Albums`(`id`)
);
INSERT INTO Music VALUES(1,'music1',1992,1,1);
INSERT INTO Music VALUES(2,'music2',1994,1,1);
INSERT INTO Music VALUES(3,'music3',2001,2,3);
CREATE TABLE IF NOT EXISTS "Albums" (
	`id`	INTEGER NOT NULL,
	`name`	TEXT NOT NULL,
	`year`	INTEGER NOT NULL,
	`artist`	INTEGER,
	`genre`	TEXT NOT NULL,
	`description`	TEXT NOT NULL,
	FOREIGN KEY(`artist`) REFERENCES `Artists`(`id`),
	PRIMARY KEY(`id`)
);
INSERT INTO Albums VALUES(1,'album2',1998,1,'Rock','');
INSERT INTO Albums VALUES(2,'album1',2001,2,'Jazz','');
CREATE TABLE IF NOT EXISTS "Users" (
	`id`	INTEGER NOT NULL,
	`name`	TEXT NOT NULL,
	`password`	TEXT NOT NULL,
	`login`	INTEGER NOT NULL,
	`editor`	INTEGER NOT NULL,
	`has_notifications`	INTEGER NOT NULL,
	PRIMARY KEY(`id`)
);
INSERT INTO Users VALUES(1,'user1','password1',0,1,0);
INSERT INTO Users VALUES(2,'user2','password2',0,0,0);
DELETE FROM sqlite_sequence;
INSERT INTO sqlite_sequence VALUES('Artists',3);
COMMIT;
