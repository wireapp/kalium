Added explicit everyone mentions for group messages and attachment captions, with unread mention counts, notifications that respect mute settings, draft/edit persistence, and backward-compatible backups.

Mention user IDs are now nullable: a null target represents everyone, exposed as `MessageMention.isEveryone`. Consumers must handle a nullable user ID before accessing a user's profile. Older clients display the literal message text; everyone-mention notification behavior requires an updated client.
