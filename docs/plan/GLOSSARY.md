---
domain: goo
type: reference
purpose: Definitions of Goo-specific terms (clique, microblob, omniblob, slurry, contact) — consult when encountering unfamiliar domain vocabulary
tags: [goo, glossary, terminology, clique, microblob, omniblob, slurry, contact, living-blob]
---

# Glossary

**Clique:** the set of items belonging to a single goo type. Fungibility is clique-based: items convert to/from their type's goo, but goo-to-goo transmutation doesn't exist.

**Contact:** the effect applied to the player the instant they throw a blob bare-handed. Gloves block all contact effects.

**Living Blob:** created by Vital goo's world effect. Stationary, player-loyal slime that attacks hostile mobs by spitting blobs. Expires over time.

**Microblob (mB):** the internal unit of goo. All values stored as `long`. 1 mB = 1 NeoForge millibucket.

**Omniblob:** unstackable, uncapped-capacity goo container item. One per goo type (15 total). Used for sub-blob remainders or volumes exceeding 64,000 mB. Stores volume via `BLOB_VOLUME` data component. See `DESIGN.md` - Storage.

**Slurry:** mixed-type goo state. Crucible reservoirs hold slurry; canisters pull only their matching type.
