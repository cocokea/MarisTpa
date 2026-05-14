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

## Player Commands

- `/tpa <player>` - Request teleport to another player.
- `/tpahere <player>` - Ask a player to teleport to you.
- `/tpaccept [player]` - Accept a teleport request.
- `/tpdeny [player]` - Deny a teleport request.
- `/tpadeny [player]` - Alias of `/tpdeny`.
- `/tpacancel` - Cancel your active request.
- `/tpauto` - Toggle automatic request handling if enabled.

## Files

- `config.yml` - Main plugin settings.
- `gui.yml` - GUI text and layout.
- `message.yml` - Messages shown to players.
- `data/players.yml` - Saved per-player data.

## Notes

- This plugin is marked as Folia supported.
- MarisSettings is optional.