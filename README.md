# MarisTpa

MarisTpa is a Folia-safe teleport request plugin for player-to-player travel.

## What It Handles

- Standard teleport requests
- Teleport-here requests
- Accept, deny, and cancel flow
- Auto accept toggle
- Optional integration with MarisSettings

## Requirements

- Paper / Folia 1.21+
- Java 21

## Installation

1. Put the plugin jar in `plugins`.
2. Start the server once.
3. Configure `config.yml`, `gui.yml`, and `message.yml`.
4. Restart the server.

## Quick Setup

1. Review default request timeout and cooldown settings in `config.yml`.
2. Open `gui.yml` if your server uses a menu-driven accept flow.
3. Test `/tpa`, `/tpahere`, `/tpaccept`, and `/tpdeny` with two accounts.
4. If you use MarisSettings, verify toggles behave correctly.

## Player Commands

- `/tpa <player>` - Request teleport to another player.
- `/tpahere <player>` - Ask a player to teleport to you.
- `/tpaccept [player]` - Accept a teleport request.
- `/tpdeny [player]` - Deny a teleport request.
- `/tpadeny [player]` - Alias of `/tpdeny`.
- `/tpacancel` - Cancel your active request.
- `/tpauto` - Toggle automatic request handling if enabled.

## Command Examples

```text
/tpa maris7
/tpahere maris7
/tpaccept
/tpdeny maris7
/tpauto
```

## Files

- `config.yml` - Main plugin settings.
- `gui.yml` - GUI text and layout.
- `message.yml` - Messages shown to players.
- `data/players.yml` - Saved per-player data.

## MarisSettings Integration

If `MarisSettings` is installed, MarisTpa can use:

- `TPA_TOGGLE`
- `TPAHERE_TOGGLE`
- `TPAGUI_TOGGLE`

## Common Mistakes

- Testing with one account only and missing the two-sided request flow.
- Forgetting that `/tpauto` changes how requests are handled on the target side.
- Leaving old player data in `players.yml` when troubleshooting stale behavior.

## Notes

- This plugin is marked as Folia supported.
- MarisSettings is optional.