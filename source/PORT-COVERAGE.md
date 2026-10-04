# Minecraft 1.21.1 port coverage

All 248 controls of Bons and Furious 1.0.30 (127 from 1.0.27, 121 added in 1.0.28 and later) are accounted for: 165 ported, 41 retired because the 1.21.1 target changed or was fixed upstream, 42 target mods unavailable for Minecraft 1.21.1 / NeoForge.

| Control | Added | Status | Explanation |
| --- | --- | --- | --- |
| `adastra_gravity_primitive` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `alexscaves_equipment_enumeration` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `alexscaves_magnet_query` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `alexscaves_rare_biome_column_memo` | 1.0.28 | target unavailable | No author-published Minecraft 1.21.1 NeoForge build of Alex's Caves (Modrinth/CurseForge query 2026-10-03, as for the other Alex's Caves controls); original patch retained in the 1.20.1 source. |
| `alexscaves_teletor_generation_context` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `alexsmobs_crow_scan_palette_check` | 1.0.30 | target unavailable | No author-published Minecraft 1.21.1 NeoForge build of Alex's Mobs (Modrinth/CurseForge query 2026-10-03, evidence/new-target-discovery-1030.json); original patch retained in the 1.20.1 source. |
| `alexsmobs_partner_min_scan` | 1.0.30 | target unavailable | No author-published Minecraft 1.21.1 NeoForge build of Alex's Mobs (Modrinth/CurseForge query 2026-10-03); original patch retained in the 1.20.1 source. |
| `almostunified_duplicate_groups` | 1.0.29 | ported | Almost Unified 1.4.2 still runs 0.11.0's quadratic duplicate loop and comparison code under new package and method names. |
| `ambientsounds_biome_match_cache` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `ambientsounds_dimension_patterns` | 1.0.27 or earlier | retired on 1.21.1 | AmbientSounds 6.3.9 compiles included and excluded dimension patterns once in init and reuses them in is(Level); the original repeated String.matches compilation is already fixed upstream. |
| `ambientsounds_terrain_scan_bound` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `architectury_event_dispatch` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `ars_direct_perk_snapshots` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `ars_primitive_mana_discounts` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `artifacts_living_tick_order` | 1.0.28 | ported | Artifacts 13.2.5 moved the checks to ArtifactHooksNeoForge.onKittySlippersLivingUpdate and UmbrellaItem.shouldGlide; same operand reorder. Documented assumption: Curios 9.5.1 rebuilds a stored inventory on the first lookup after a load, so an attacker with Curios slots that never ticked, was tracked or looked up since loading and is saved before any lookup keeps its stored Curios data as loaded until its next lookup. Keeps Artifacts' order when Accessories is installed. |
| `bettercombat_offhand_lookup` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `biomeswevegone_foreign_chunk_skip` | 1.0.28 | retired on 1.21.1 | Oh The Biomes We've Gone 2.6.0 runs both terrain passes from ChunkStatusTasksMixin.injectExtensions and tests columns against the biome source (BiomeManager.withDifferentSource, cached per column) instead of the chunks' biome palettes, so the 1.20.1 palette proof no longer holds; an exact skip would need a new design. |
| `bomd_block_cache_presence` | 1.0.30 | ported | Bosses of Mass Destruction 1.3.3 keeps ChunkBlockCache (same fingerprint), both of its hooks and the three scans; the cache now lives in a never-saved SavedData (guarded). |
| `butterflies_landing_rule_lookup` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `chunksending_expiry_queue` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `citadel_model_vertices` | 1.0.28 | ported | Citadel 2.7.1 doRender now takes the 1.21 packed colour; the two per-vertex vector constructions are unchanged, so the same redirects apply. |
| `citreforged_lazy_stack_caches` | 1.0.30 | target unavailable | No Minecraft 1.21.1 NeoForge build of CIT Reforged (Modrinth/CurseForge query 2026-10-03); original patch retained in the 1.20.1 source. |
| `cofh_translucent_renderer_memo` | 1.0.28 | target unavailable | No CoFH Core build for Minecraft 1.21.1 NeoForge on Modrinth or CurseForge (evidence/new-target-discovery.json, 2026-10-03); original patch retained in the 1.20.1 source. |
| `colorwheel_phase_labels` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `cookingforblockheads_compat_reload_once` | 1.0.30 | retired on 1.21.1 | Cooking for Blockheads 21.1.24 has no JSON compat loader; its registry reload clears and rebuilds, so nothing grows per reload. |
| `create_collision_single_pass` | 1.0.30 | retired on 1.21.1 | Create 6.0.10 already builds contraption collision from each block's own boxes (Contraption.invalidateColliders fills a CollisionList); the join/optimize chain the switch shortened is gone. |
| `create_item_helper_empty_slots` | 1.0.30 | retired on 1.21.1 | Create 6.0.10's ItemHelper.extract already skips empty slots before getMaxStackSize and the simulated extraction. |
| `create_mounted_slot_index` | 1.0.30 | retired on 1.21.1 | Create 6.0.10 already ships it (PR #9706): MountedItemStorageWrapper has its own getIndexForSlot backed by a slotToStorage table. |
| `create_single_pass_extraction` | 1.0.30 | retired on 1.21.1 | Create 6.0.10's InvManipulationBehaviour.extract already makes a single extraction call with no simulated pre-pass. |
| `crittersandcompanions_red_panda_gate` | 1.0.30 | ported | Critters and Companions 2.7.0 moved RedPandaEntity to io.github.bonsaistudi0s...common.entity; NeutralMobsMixin adds the same goal. |
| `crypticfoes_howler_bone_cache` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `crypticfoes_sound_listener_invariant` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `cucumber_tile_dispatch_range_first` | 1.0.28 | ported | Cucumber 1.21.1-8.0.16 has the same dispatchToNearbyPlayers / isPlayerNearby bodies as 7.0.16; same overwrite. |
| `curios_foreign_capability_fast_path` | 1.0.27 or earlier | retired on 1.21.1 | Curios 9.5.1 uses NeoForge registered capabilities; the old Forge LazyOptional capability provider and getCapability method no longer exist. |
| `curios_lazy_modifier_map` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `curios_slot_map_lookup` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `curios_slotless_tick_skip` | 1.0.28 | retired on 1.21.1 | NeoForge 21 capabilities: Curios 9.5.1's provider is looked up per entity type and returns null for slotless entities; the per-entity provider walk and the CapabilityDispatcher state the 1.20.1 skip keyed on are gone. |
| `curios_tag_predicate_keys` | 1.0.30 | ported | Curios 9.5.1's curios:tag validator (now lambda$static$8) builds the same tag locations via ResourceLocation.fromNamespaceAndPath; no pinned target hooks ResourceLocation, TagKey or ItemTags. |
| `curios_thread_safe_caches` | 1.0.30 | ported | [FIX] Curios 9.5.1 still fills a plain static HashMap from SlotAttribute.getOrCreate (now Holder<Attribute>); the 1.20.1 slot-UUID half has no 1.21.1 counterpart (getUuid is gone), so only the slot-attribute cache is guarded. |
| `distanthorizons_adjacent_face_skip` | 1.0.29 | ported | Distant Horizons 3.3.3 ColumnBox byte-identical to 3.3.2. |
| `distanthorizons_biome_blend_memo` | 1.0.28 | ported | Distant Horizons 3.3.3 tryGetBlockTint byte-identical to 3.3.2. |
| `distanthorizons_c2me_direct_reads` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `distanthorizons_cloud_pass_invariants` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `distanthorizons_cloud_scalars` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `distanthorizons_ignored_dimension_match` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `distanthorizons_lod_biome_memo` | 1.0.28 | ported | Distant Horizons 3.3.3 LodDataBuilder identical; ChunkWrapper getBiome differs only by Mojang names. |
| `distanthorizons_pooled_string_index` | 1.0.29 | ported | Distant Horizons 3.3.3: FullDataPointIdMap/StringPool byte-identical to 3.3.2. |
| `distanthorizons_quad_sort_keys` | 1.0.29 | ported | Distant Horizons 3.3.3 LodQuadBuilder/BufferQuad byte-identical to 3.3.2. |
| `distanthorizons_render_param_inverse_reuse` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `distanthorizons_rough_surface_xz_cache` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `distanthorizons_sql_script_lookup_index` | 1.0.30 | ported | Distant Horizons 3.3.3 DatabaseUpdater.getAutoUpdateScripts is byte-identical to 3.3.2; the lookup index was re-derived for NeoForge 21.1's securejarhandler 3.0.8 / modlauncher 11.0.5 and checks those versions at runtime. |
| `distanthorizons_unlocked_byte_stream` | 1.0.29 | ported | Distant Horizons 3.3.3 DhDataInputStream byte-identical to 3.3.2. |
| `distanthorizons_update_queue_wait` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `distanthorizons_world_change_biome_reset` | 1.0.30 | ported | [FIX] Distant Horizons 3.3.3 setDhWorld has the same branches and anchors; the five maps keep their names. |
| `distanthorizons_wrapper_air_flag` | 1.0.29 | ported | Distant Horizons 3.3.3 wrapper isAir unchanged; NeoForge 21.1 BlockStateBase.isAir is the same final-field read as Forge 47. |
| `dungeonsdelight_yam_single_add` | 1.0.30 | ported | [FIX] Dungeon's Delight 1.5.1 still adds each summoned zombie twice on Hard; NeoForge 21.1 still posts EntityJoinLevelEvent before the duplicate-UUID refusal. |
| `dynamictrees_rot_cycle_guard` | 1.0.30 | ported | [FIX] Dynamic Trees 1.7.2 (package com.dtteam.dynamictrees) keeps the rot code of 1.4.11 and 1.21.1's WorldGenRegion still refuses far writes, so the endless rapid-rot loop is still reachable. |
| `dynamictrees_thick_shape_memo` | 1.0.30 | ported | Shell half only: Dynamic Trees 1.7.2 already precomputes the core trunk shapes; trunk shells still build a shape per query (hook now lambda$getShape$3). |
| `embeddium_direct_upload_preparation` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_draw_batch_cache` | 1.0.30 | ported | Embeddium 1.0.15 keeps 0.3.31's draw-batch fill inputs and all six writers under the relocated packages. |
| `embeddium_entity_sort_radix` | 1.0.30 | ported | Vanilla VertexSorting and its sorts are identical on 1.21.1 apart from the method name; buffers reach it through Embeddium's MeshData$SortState overwrite, the same redirected call. |
| `embeddium_lazy_completed_jobs` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_merged_draws` | 1.0.30 | ported | Same fill and shared index-buffer code on Embeddium 1.0.15, whose shaders use no gl_PrimitiveID/gl_DrawID/gl_BaseVertex. |
| `embeddium_pending_upload_sum` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_search_replay` | 1.0.30 | ported | The visibility search, its key inputs and both epoch writers are 0.3.31's code under Embeddium 1.0.15's packages (the frustum check now names the relocated package). |
| `embeddium_section_cache_prefix_cleanup` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_serializer_lookup_snapshot` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_sprite_tick_frame_times` | 1.0.29 | ported | Embeddium 1.0.15 keeps the same preTick logic (options via org.embeddedt.embeddium.impl.Embeddium.options()); the Ticker's parent is captured at its constructor because 1.21.1's Ticker has no this$0 field. |
| `embeddium_upload_classification` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_visible_faces_first` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_weighted_pick_table` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `emf_block_entity_type_string` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `emf_cube_vertex_vector` | 1.0.29 | retired on 1.21.1 | Entity Model Features 3.3.9 EMFCube.compile (like vanilla 1.21.1 ModelPart$Cube.compile) creates one Vector3f per cube and reuses it; the per-vertex allocation the 1.20.1 switch removed no longer exists. |
| `emf_hierarchical_id_memo` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `emf_variable_index` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `entityculling_dirty_cache_reset` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `etf_sprite_texture_id_memo` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `fancymenu_render_thread_state` | 1.0.28 | ported | FancyMenu 3.9.14 render-state util classes are identical to 3.9.14 for 1.20.1 apart from Minecraft names. |
| `farmersdelight_overlay_lookup_memo` | 1.0.30 | retired on 1.21.1 | Farmer's Delight 1.3.4 for 1.21.1 registers its HUD as NeoForge GUI layers; NeoForge 21.1 has no GuiOverlayManager.findOverlay, so the per-frame lookup is gone. |
| `farmersdelight_tool_action_items` | 1.0.27 or earlier | retired on 1.21.1 | Farmer's Delight 1.3.4 for 1.21.1 replaces ToolActionIngredient and its eager constructor/packet decoder with a codec and lazy ItemAbilityIngredient.getItems(). The old repeated constructor/decoder scan no longer exists; no eager scan is added by this port. |
| `fdbosses_phase_sphere_local_only` | 1.0.30 | ported | [FIX] The use counter's reader moved to BossClientModEvents and a client-only death reset was added; both are still local-player state, so the fix is the same. |
| `fdbosses_spawner_presence_gate` | 1.0.30 | ported | Qliphoth Awakening 3.1.0.3 for 1.21.1 makes the same six calls with the same owners and descriptors. |
| `forge_object_holder_pass_index` | 1.0.29 | retired on 1.21.1 | NeoForge 21.1.252 has no ObjectHolderRegistry or RegistryObject; GameData.postRegisterEvents runs no holder pass and DeferredHolder binds once, lazily. |
| `forge_plain_translation_format` | 1.0.27 or earlier | retired on 1.21.1 | ForgeI18n moved to FMLTranslations in the separate early loader module; NeoForge mod mixins cannot transform classes already loaded by that module. |
| `forge_render_layer_memo` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `forge_status_ping_channel_groups` | 1.0.27 or earlier | retired on 1.21.1 | NeoForge 21.1 networking no longer has ServerStatusPing or its per-mod channel scan. |
| `forge_status_ping_memo` | 1.0.29 | retired on 1.21.1 | NeoForge 21.1 has no ServerStatusPing; MinecraftServer.buildServerStatus builds vanilla's ServerStatus without a mod/channel list and NeoForge caches the JSON. |
| `fowlplay_air_targets_loaded_only` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `fowlplay_flock_vectors` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `frame_pacing` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `fusion_chunk_layer_id` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `fusion_connection_lookups` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `geckolib_animation_hashes` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `geckolib_bone_queue_reuse` | 1.0.28 | ported | GeckoLib 4.9.3 moved AnimationController/BoneAnimationQueue to software.bernie.geckolib.animation; createInitialQueues and the queue polling are unchanged. |
| `geckolib_keyframe_locals` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `geckolib_primitive_easing` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `geckolib_quad_vectors` | 1.0.28 | ported | GeckoLib 4.9.3 createVerticesOfQuad still allocates a Vector4f per vertex; the overwrite now writes the 1.21 packed colour. |
| `goblins_animation_entity_guard` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `goblins_cached_disguise_tag` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `hostilevillages_distant_generation_queue` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `hostilevillages_pending_chunk_queue` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `iceandfire_entity_tag_keys` | 1.0.30 | target unavailable | No author-published Minecraft 1.21.1 NeoForge build of Ice and Fire (as for its earlier controls); original patch retained in the 1.20.1 source. |
| `iceandfire_lake_guard_region` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `iceandfire_lazy_reference_lists` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `iceandfire_path_debug_stage_gate` | 1.0.30 | target unavailable | No author-published Minecraft 1.21.1 NeoForge build of Ice and Fire; original patch retained in the 1.20.1 source. |
| `iceandfire_pixie_village_difficulty` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `iceandfire_tabula_lookup_index` | 1.0.30 | target unavailable | No author-published Minecraft 1.21.1 NeoForge build of Ice and Fire; original patch retained in the 1.20.1 source. |
| `ie_multiblock_sound_replaced` | 1.0.30 | retired on 1.21.1 | Fixed upstream: Immersive Engineering 12.4.2's earmuffs scale volume (EarmuffHandler + its own SoundEngine mixin) instead of replacing the sound instance; onPlaySound and IEMuffledTickableSound are gone. |
| `immediatelyfast_idle_layer_skip` | 1.0.28 | retired on 1.21.1 | ImmediatelyFast 1.6.14 rewrote BatchableBufferSource for the 1.21 ByteBufferBuilder; endBatch on an idle layer no longer builds the per-layer set and iterator the 1.20.1 switch removed (getOrCreateBufferBuilder/getBufferBuilder are gone). |
| `immediatelyfast_known_core_shaders` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `immediatelyfast_offset_layer_prefixes` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `jei_baked_index_background_grams` | 1.0.30 | ported | JEI 19.51.0.418's BakedSubstringIndex and builder decompile identical; ElementSearch still puts and builds on one thread. |
| `jei_server_item_registry` | 1.0.27 or earlier | retired on 1.21.1 | JEI 19 uses RegistryUtil.getRegistry returning the Minecraft Registry directly; the per-ingredient IPlatformRegistry wrapper allocation removed by this patch no longer exists. |
| `jei_sort_index_keys` | 1.0.30 | ported | JEI 19.51's comparator chain, sorting configs and stage enum are identical; the alphabetical key now follows getNames().getFirst() as JEI does. |
| `jei_typed_stack_cache` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `kiwi_manifest_lookup_index` | 1.0.30 | retired on 1.21.1 | Kiwi 15.8.7 loads each mod's metadata through that mod's own file (IModFile.findResource), so the class-loader scan of every jar the switch indexed is gone. |
| `l2library_effect_icon_fast_path` | 1.0.30 | retired on 1.21.1 | In L2 Core 3.0.8 the effect data is one NeoForge attachment read; the repeated capability-provider walk the switch removed no longer exists. |
| `l2library_stop_tracking_noop` | 1.0.30 | ported | [FIX] L2 Core 3.0.8 (inside L2 Library 3.0.8) still sends 'effect removed' for still-active tracked effects when a player stops tracking an entity; target mod id is now l2core. |
| `legendary_monsters_camera_null_guard` | 1.0.30 | ported | [FIX] Legendary Monsters 2.2.3's ClientEvent.onCameraSetup dereferences Minecraft.player with no null check at all. |
| `lionfishapi_fluid_walk_scan` | 1.0.30 | ported | LionfishAPI 3.1's EntityMixin handler fluidCollision(Vec3) has the same instructions as 2.8 apart from Mojang names and NeoForge.EVENT_BUS. |
| `lionfishapi_model_vertices` | 1.0.28 | ported | LionfishAPI 3.1 keeps the same vertex loop with the packed-colour descriptor. |
| `modernfix_bake_location_order` | 1.0.30 | ported | ModernFix 5.27.24's ModelBakeEventHelper (now under its neoforge package) keeps the same single ObjectLinkedOpenHashSet(int); entries are ModelResourceLocation records. |
| `modernfix_c2me_cache_strongholds` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `modernfix_cube_bake_memo` | 1.0.30 | ported | CubeDefinition.bake is unchanged and ModernFix 5.27.24's CubeDefinitionMixin decompiles identically to 5.27.77's; stands down without ModernFix's mixin. |
| `modernfix_represented_tabs_index` | 1.0.30 | retired on 1.21.1 | ModernFix 5.27.24 has no searchtree package, JEIRuntimeCapturer or JEI reference; the represented-tabs scan the switch indexed is gone. |
| `moon_animation_entity_guard` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `mowziesmobs_capability_handles` | 1.0.28 | retired on 1.21.1 | Mowzie's Mobs 1.8.2 has no capability code any more; DataHandler.getData is an attachment lookup (entity.getData). |
| `mutantmonsters_empty_shoulder_skip` | 1.0.30 | ported | Mutant Monsters v21.1.1 renamed the listener to onEndPlayerTick; playShoulderEntitySound is the same code and an empty id still resolves to no entity type. |
| `netherdepths_reuse_enchantment_decode` | 1.0.27 or earlier | retired on 1.21.1 | Nether Depths Upgrade 3.3 uses enchantment data components and a single getHellStriderLevel call; the two NBT-to-map decodes removed by the 1.20.1 patch no longer exist. |
| `netherexp_antidote_effect_guard` | 1.0.30 | retired on 1.21.1 | Fixed upstream: Jaden's Nether Expansion 2.4.1 replaced the throwing NBT string parse with a data component decoded by ResourceLocation.CODEC and an Optional lookup. |
| `occult_bed_scan_guard` | 1.0.27 or earlier | retired on 1.21.1 | The available NeoForge 1.21.1 release, Occult alpha-2-b, has no Tendrils entity or TendrilsBedProcedure. The expensive scan introduced in the 1.20.1 Alpha 5 target is absent. |
| `oculus_dh_instance_lookup` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_empty_sampler_fast_return` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_empty_transparency_graphs` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_entity_vertex_reuse` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_phase_labels` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_primitive_buffer_affinities` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_primitive_uniform_locations` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_program_traversal` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_reuse_empty_graphs` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_shadow_edge_vectors` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_shadow_outline_discard` | 1.0.30 | ported | Iris 1.8.12 still never ends the shadow pass's outline batch (GeckoLib 4.9.3 glowing armour still writes into it); the discard was rewritten for the 1.21 buffer API (MeshData.close). Targets Iris with Sodium 0.6.13. |
| `oculus_shadow_search_replay` | 1.0.30 | target unavailable | Oculus has no Minecraft 1.21.1 build; Iris 1.8.12 declares Embeddium incompatible and its shadow frusta are Sodium's, so no Oculus shadow search reaches Embeddium's culler. Original patch retained in the 1.20.1 source. |
| `particular_chest_waterlogged_memo` | 1.0.30 | target unavailable | No Minecraft 1.21.1 NeoForge build of Particular (Modrinth/CurseForge query 2026-10-03); original patch retained in the 1.20.1 source. |
| `pehkui_scale_tick_callbacks` | 1.0.28 | ported | Pehkui 3.8.3 tickScale and ScaleType are byte-identical to 3.8.2 (same guard fingerprints). |
| `pipez_empty_filter_fast_path` | 1.0.30 | ported | Item and fluid pipes: Pipez 1.2.31's canInsert gained a HolderLookup.Provider argument an empty filter list never uses. Gas pipes keep Pipez's own check (no Mekanism 1.21.1 jar pinned). |
| `pipez_sorted_connections_memo` | 1.0.30 | ported | Pipez 1.2.31 has the same getSortedConnections/getConnections, final getDistance and per-side cached configuration; only the PipeType generics changed. |
| `placebo_enchantment_event_skip` | 1.0.28 | retired on 1.21.1 | Placebo 9.9.2 has no PlaceboEventFactory or GetEnchantmentLevelEvent; the event belongs to NeoForge (EventHooks.getEnchantmentLevelSpecific / getAllEnchantmentLevels), so a patch would be a new design. |
| `presencefootsteps_duplicate_tracking` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `radium_c2me_chunk_access` | 1.0.27 or earlier | retired on 1.21.1 | Radium 0.13.1 has no applyC2MECompat blanket disable, and getChunkBlocking already calls holder.scheduleChunkGenerationTask; the old bypass repair and option override are unnecessary. |
| `radium_c2me_player_chunk_tick` | 1.0.27 or earlier | retired on 1.21.1 | Radium 0.13.1 has neither the applyC2MECompat blanket disable nor the old world.player_chunk_tick mixin. |
| `radium_entity_touchable_memo` | 1.0.29 | retired on 1.21.1 | Radium 0.13.1 (BlockStateFlags$5 -> ReflectionUtil.isBlockStateEntityTouchable) already caches the answer per block class; the per-state reflective walk is gone. |
| `radium_experimental_tickets_spawning` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `radium_hash_palette_copy` | 1.0.30 | ported | [FIX] Radium 0.13.1's hash palette copy() still uses new Reference2IntOpenHashMap(table) and loses the -1 default; idFor unchanged. |
| `radium_part_entity_collisions` | 1.0.30 | ported | [FIX] Same shortcut and redirect site on Radium 0.13.1 (now also reached from appendEntityCollisions, guarded). Known gap: Radium 0.13.1's new intersection.EntityViewMixin sends EntityGetter.getEntityCollisions to a second copy that also drops part entities; covering it would be a new design. |
| `radium_untrack_chunk_hooks` | 1.0.27 or earlier | retired on 1.21.1 | Radium 0.13.1 removed world.player_chunk_tick.ThreadedAnvilChunkStorageMixin; its cancelled Forge tracking hooks no longer exist. |
| `radium_world_border_keep_listeners` | 1.0.30 | ported | [FIX] Radium 0.13.1 keeps the same border listener logic (reset callback renamed lithium$onWorldBorderShapeChange); the vanilla setters still notify every listener. |
| `regionsunexplored_fuel_burn_memo` | 1.0.30 | retired on 1.21.1 | Regions Unexplored 0.6.2 has no FurnaceFuelBurnTimeEvent listener any more (FurnaceBurnTimes only builds four item lists nothing reads). |
| `relics_fluid_walk_scan` | 1.0.30 | ported | Relics 0.10.7.8's fluidCollision(Vec3) handler has the same instructions as 0.8.0.13 apart from names and the event bus; no Relics code carried. |
| `relics_foreign_capability_fast_path` | 1.0.27 or earlier | retired on 1.21.1 | Relics 0.10.7.8 removed IRelicsCapability and the Forge LazyOptional capability provider targeted by this patch. |
| `ribbits_performer_absent_guard` | 1.0.30 | ported | [FIX] Ribbits 4.1.6's PlayerInstrumentTracker.removePerformer has the same fingerprint as 3.0.2 and still has no null check. |
| `ryoamiclights_block_entity_lock_skip` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `ryoamiclights_chunk_iteration` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `sakes_structure_check_once` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `scorched_sandcrab_burrow_gate` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `scuba_gear_generation_context` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `simplyswords_config_unchanged_reuse` | 1.0.30 | retired on 1.21.1 | Simply Swords 1.70.2 loads its config once through Fzzy Config; the per-read json5 parse the switch skipped is gone. |
| `slashblade_motion_update_memo` | 1.0.30 | retired on 1.21.1 | SlashBlade: Resharped 2.0.7 removed the cost itself: VmdAnimation.setupAnim returns early on a repeated (tick, partial tick) and nothing calls the patched MmdMotionPlayer.updateMotion(F)V any more. |
| `sliceanddice_wet_air_gate` | 1.0.30 | retired on 1.21.1 | Create Slice & Dice 4.3.4 has no wet-air block and no Level/Entity mixins; sprinklers act through SprinklerBehaviour, so the gated scan no longer exists. |
| `spawn_flyless_particle_check` | 1.0.29 | retired on 1.21.1 | Spawn 4.0.8 calls FlyData.spawnRandomFlyParticles only for entities that have flies (hasData(FLIES) and a non-empty list), so the 0-flies path the switch answered is never reached. |
| `spawn_sealife_tick` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `spawn_sealife_waterlogged_memo` | 1.0.30 | ported | Spawn 4.0.8's sea-life tick still opens with getValue(WATERLOGGED); documented: 1.21.1's StateHolder.getValues() returns its mutable map, which a scan found only read and copied. |
| `spawn_zombified_flower_floor` | 1.0.27 or earlier | retired on 1.21.1 | Spawn 4.0.8 already checks pos.getY() > worldgenlevel.getMinBuildHeight() before its downward flower-placement loop, so the original fix is included upstream. |
| `storagedrawers_count_label_memo` | 1.0.28 | ported | Storage Drawers 13.11.4 moved the five String.format calls into formatApprox(Font, int); the selector and guard name that overload. |
| `storagedrawers_count_sync_holders` | 1.0.30 | ported | Storage Drawers 13.11.4's count update still ends in one broadcast loop (Chameleon -> PacketDistributor.sendToPlayersNear -> PlayerList.broadcast); the filter now matches payload id storagedrawers:count_update. |
| `structure_gel_lake_guard_region` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `structurify_check_memo_slim` | 1.0.30 | ported | Structurify 2.0.42 keeps the same lambda and three inner computeIfAbsent calls with write-only inner maps; its new releaseOverlapClaims was re-checked and guarded. |
| `structurify_height_cache` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `structurify_set_data_single_lookup` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `tacz_sync_collections` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `tacz_sync_scope` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `terrablender_lazy_namespace_rules` | 1.0.28 | ported | TerraBlender 4.1.0.8 NamespacedSurfaceRuleSource.apply and vanilla SurfaceRules are unchanged; stands aside when C2ME's allocation module is active, as on 1.20.1. |
| `terrablender_namespace_rule_memo` | 1.0.28 | ported | TerraBlender 4.1.0.8 NamespacedRule.tryApply makes the same calls in the same order as 3.0.1.10; the LGPL overwrite body was re-derived from 4.1.0.8. |
| `terrain_density_memo` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `terrain_final_density_reuse` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `terrain_surface_estimate_share` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `terramity_animation_entity_guard` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_backoff_nan` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_chunk_bookkeeping` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_chunk_set_version` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_collision_axes` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_entity_base_tick` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_entity_handlers` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_entity_ship_collision` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_entity_unloads` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_render_interpolation` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_sculk_vibrations` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_ship_lookups` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_spawn_distance` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_standing_probe` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_terrain_snapshot_order` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_weather_occlusion` | 1.0.27 or earlier | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `vanilla_animate_tick_uniform_biome` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_aquifer_candidate_cache` | 1.0.30 | ported | Aquifer$NoiseBasedAquifer is semantically unchanged on 1.21.1; same wrap, vanilla's computeFluid order kept. Stands aside when any other mod (e.g. C2ME 0.4) mixes into NoiseBasedAquifer. |
| `vanilla_aquifer_high_air` | 1.0.30 | ported | Interface marker plus the FluidStatus.fluidLevel accessor; FluidStatus is unchanged on 1.21.1. |
| `vanilla_background_level_dat` | 1.0.30 | retired on 1.21.1 | 1.21.1's saveLevelData moved to Path/SYNC writes; a background level.dat writer would need a new design and proof (the Forge switch is off by default too). |
| `vanilla_background_saves` | 1.0.30 | retired on 1.21.1 | NeoForge 21.1 already writes all SavedData in the background (most of the 1.20.1 saving); the remaining synchronous player/advancement/stat writes moved to Path/SYNC APIs and would need a new design and proof. |
| `vanilla_beardifier_influence_bounds` | 1.0.30 | ported | Adapted to 1.21.1's Beardifier (getBuryContribution now (DDD)D; the new ENCAPSULATE term gets a [min-11, max+11] box); checked exhaustively: 6,555,155 terms, every one outside its box exactly +0.0. |
| `vanilla_biome_fiddle_mask` | 1.0.30 | ported | BiomeManager.getFiddle is identical on 1.21.1 (same fingerprint as 1.20.1). |
| `vanilla_block_entity_tick_state` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_block_state_air_flag` | 1.0.29 | ported | NeoForge 21.1 still answers isAir() from Block.isAir(state), a final field; only the BlockStateBase constructor descriptor changed (Reference2ObjectArrayMap). |
| `vanilla_block_ticking_range_memo` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_camera_fluid_memo` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_chunk_status_name_memo` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_climate_rtree_flat_bounds` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_climate_sample_repeat` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_climate_search_repeat` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_climate_tree_sort_keys` | 1.0.29 | ported | Climate.RTree sort and comparator read the same in 1.21.1. |
| `vanilla_climate_tree_span_bounds` | 1.0.29 | ported | Climate.RTree buildParameterSpace and Parameter.span read the same in 1.21.1. |
| `vanilla_connection_flush_batching` | 1.0.30 | retired on 1.21.1 | Minecraft 1.21.1 already batches network flushes per tick and player (MinecraftServer.tickChildren suspends/resumes flushing; sends pass flush=false), which was the measured 1.20.1 gain. The remaining Netty lazyExecute detail is unmeasured on 1.21.1 and left out (port kept in retired/1.0.30/network_flush). |
| `vanilla_cursor_counters` | 1.0.30 | ported | Cursor3D is identical on 1.21.1. |
| `vanilla_data_merge_unchanged` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_entity_class_count_layer` | 1.0.30 | ported | Same count layer; its search-method set was rebuilt for Mojang names (the SRG list collapsed to duplicates, which Set.of rejects). Only Radium's after-call injects touch the sections, and they compose. |
| `vanilla_entity_section_x_overflow` | 1.0.30 | ported | [FIX] EntitySectionStorage and the SectionPos packing are unchanged on 1.21.1. |
| `vanilla_fire_scan_loop` | 1.0.28 | ported | The fire/lava check in Entity.move and Level.getBlockStatesIfLoaded are the same code on 1.21.1. |
| `vanilla_fog_color_sample_memo` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_framed_map_holder_scan` | 1.0.30 | ported | Adapted: 1.21.1's tickCarriedBy uses Inventory.contains(Predicate); the wrap targets that call with the same contains || isFramed() result. |
| `vanilla_game_event_registry_lookup` | 1.0.30 | ported | Only descriptors changed (Holder<GameEvent>); bodies identical. Steps aside for Radium's game-event dispatch mixin. |
| `vanilla_goal_flags_none_disabled` | 1.0.30 | ported | GoalSelector.goalContainsAnyFlags is identical on 1.21.1. |
| `vanilla_item_merge_candidates` | 1.0.30 | ported | Adapted to 1.21.1's areMergable order (count test, then isSameItemSameComponents) and NeoForge's IItemExtension; steps aside for Radium's item-merging mixin. |
| `vanilla_layer_bake_streamless` | 1.0.30 | ported | PartDefinition.bake is unchanged on 1.21.1 and Guava 32.1.2's toImmutableList is still builder/add/build. |
| `vanilla_long_jump_weighted_pick` | 1.0.30 | ported | Targets identical on 1.21.1; steps aside for Radium 0.13.1's long_jump_weighted_choice mixin (on by default, so with default Radium this switch yields). |
| `vanilla_model_bone_lookup` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_noise_column_cache` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_noise_wrap_presize` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_poi_chunk_sections` | 1.0.27 or earlier | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_raycast_fluid_none` | 1.0.30 | ported | Same fast path; the stand-down is now generic: any other mixin merged into BlockGetter.clip or its helpers (Radium's world.raycast, Valkyrien Skies) turns it off with one INFO line. |
| `vanilla_repellent_search_sections` | 1.0.30 | ported | Targets identical on 1.21.1; only withinManhattan's iterator class is now BlockPos$3. |
| `vanilla_selector_type_index` | 1.0.29 | ported | Adapted to the 1.21 selector rewrite (predicate list, Util.allOf left-to-right chain, AABB, feature flags); the wrapped ServerLevel.getEntities call, type-option lambdas and EntityLookup are unchanged. |
| `vanilla_spawn_gate_visibility_memo` | 1.0.30 | ported | Same ServerChunkCache.tickChunks gate on 1.21.1; the loop chunk moved from slot 16 to 14, PersistentEntitySectionManager is still the only reader of chunkVisibility. |
| `vanilla_strip_formatting_fast_path` | 1.0.30 | ported | ChatFormatting.stripFormatting and its pattern are the same on 1.21.1; JDK 21's Matcher.replaceAll still returns its argument when nothing matches. |
| `vanilla_suffocation_scan_loop` | 1.0.28 | ported | Entity.isInWall and BlockPos.betweenClosedStream are the same code on 1.21.1 apart from dimensions.width(); same positions, same order. |
| `vanilla_sun_burn_deferred_wet_check` | 1.0.30 | ported | Adapted: 1.21.1's isInBubbleColumn fills Entity's inBlockState memo as a side effect, so the wet check is deferred only while that memo is already set (LivingEntity.baseTick sets it for every dry mob first). |
| `vanilla_tag_membership_ids` | 1.0.30 | ported | Same Holder$Reference tag membership code; the selector names is(TagKey) among its five Mojang-named overloads. |
| `vanilla_ticker_range_memo` | 1.0.30 | ported | Same Level.tickBlockEntities loop on 1.21.1; the ticker local moved from slot 3 to 4 (runsNormally now in 3), selectors descriptor-qualified. |
| `vanilla_ticking_chunk_memo` | 1.0.29 | ported | Adapted to 1.21.1's ChunkResult futures (only the game's final Success/Fail records are remembered; any non-vanilla holder class always runs the method). Stands aside whenever C2ME is installed, because C2ME 0.4 replaces the chunk holders. |
| `vanilla_turtle_egg_search_sections` | 1.0.30 | ported | Adapted: Forge's canEntityDestroy hooks became NeoForge's IBlockExtension/IBlockStateExtension (same name and body); override scan and guards updated. |
| `worldgen_empty_beardifier_marker` | 1.0.28 | ported | 1.21.1 Beardifier.compute only gained an ENCAPSULATE case inside the piece loop, which an empty Beardifier never enters; createNoiseChunk and the NoiseChunk fill path are unchanged. Stands aside by design when C2ME rewrites the same code. |
