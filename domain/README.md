# Domain Layer

Feature-specific business logic organized by capabilities and use cases.

**Modules:**
- `backup` - Backup creation and restoration functionality
- `calling` - Voice/video calling features
- `cells` - Cells SDK integration for secure file sharing
- `conversation-history` - Conversation history management
- `messaging/sending` - Message sending logic
- `messaging/receiving` - Message receiving logic

- **Dependencies:** Core layer and Data layer
- **Used by:** Logic layer
- **Guidelines:** Each domain module should focus on a single feature or capability.
