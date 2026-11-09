# MobPowers (Paper / Minecraft Java 26.1)

A Java 25 Paper plugin project. Build with `./gradlew build` (or `gradle build`); the distributable plugin is `build/libs/MobPowers-1.0.0.jar`. Put the JAR in the server's `plugins/` directory and start Paper 26.1.

## Included

- UUID-keyed, disk-persisted mob assignment and absolute cooldown timestamps. First-time power is persisted **before** roulette starts, so disconnects/restarts cannot reroll it.
- First-join animation, configurable isolation, optional selection location, rarity-colored announcements, item in slot 1 (inventory index 0), protected item, cooldown messages, GUI, and administrator commands.
- The catalog is generated from every spawnable living `EntityType` provided by the target server API, with per-mob names and descriptions and curated powers/rarities for the requested headline mobs. Common activation mechanics include explosions that never damage blocks by default, projectiles, teleport/dash, flight, buffs, defense and status effects.
- Rarity/cooldown, selection location, flight, explosion, and item settings in `config.yml`; per-mob `mobs.<ENTITY_TYPE>.rarity`, `.cooldown`, `.ability`, and `.description` overrides are supported.

## Commands

`/mobpowers` opens the read-only information menu. Admins (permission `mobpowers.admin`, default OP) can use `reload`, `give <player> <mob>`, `remove <player>`, `reset <player>`, `info <player>`, `roulette <player>`, `setrarity <mob> <rarity>`, and `setcooldown <mob> <seconds>`.

## Version/build note

Target is Paper 26.1 and Java 25. The Gradle build pins Paper API `26.1.2.build.23-alpha`; if that API build has been superseded or is not present in your configured Maven mirror, replace it with a published 26.1 API build. This repository does not include a Gradle wrapper or a server binary. Test on a staging server before production.

## Limitations

Minecraft adds entities and API details between releases. The catalog automatically covers living mob types present in the Paper API; custom/non-living entities are not powers. The implementation uses shared mechanic families for many of the catalog entries, not an entirely bespoke animation/skill implementation for every mob. This is a practical plugin foundation, not a claim that every requested advanced mechanic (such as true wall crawling, configurable GUI art, or custom cinematic roulette icons) is implemented. Confirm the dependency resolves and run `gradle build` against the exact Paper 26.1 distribution before deployment.
