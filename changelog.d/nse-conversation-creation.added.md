Added NSE receiving for new Proteus and MLS groups/channels, prepared-client MLS
welcomes and subsequent text. Conversation metadata, members, profiles, system
messages and verification effects persist atomically, with canonical replay across
the SQL/native commit boundary. One-to-one resolution, resets and removed-group
recovery remain unsupported; legal-hold NSE no-ops are unchanged.
