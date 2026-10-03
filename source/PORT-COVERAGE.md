# Minecraft 1.21.1 port coverage

All 127 original controls are accounted for. The port ships 82 controls; 13 are retired because their original target changed or was fixed upstream; 32 target mods unavailable for this Minecraft/loader combination.

| Original control | Status | Explanation |
| --- | --- | --- |
| `adastra_gravity_primitive` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `alexscaves_equipment_enumeration` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `alexscaves_magnet_query` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `alexscaves_teletor_generation_context` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `ambientsounds_biome_match_cache` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `ambientsounds_dimension_patterns` | retired on 1.21.1 | AmbientSounds 6.3.9 compiles included and excluded dimension patterns once in init and reuses them in is(Level); the original repeated String.matches compilation is already fixed upstream. |
| `ambientsounds_terrain_scan_bound` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `architectury_event_dispatch` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `ars_direct_perk_snapshots` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `ars_primitive_mana_discounts` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `bettercombat_offhand_lookup` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `butterflies_landing_rule_lookup` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `chunksending_expiry_queue` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `colorwheel_phase_labels` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `crypticfoes_howler_bone_cache` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `crypticfoes_sound_listener_invariant` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `curios_foreign_capability_fast_path` | retired on 1.21.1 | Curios 9.5.1 uses NeoForge registered capabilities; the old Forge LazyOptional capability provider and getCapability method no longer exist. |
| `curios_lazy_modifier_map` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `curios_slot_map_lookup` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `distanthorizons_c2me_direct_reads` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `distanthorizons_cloud_pass_invariants` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `distanthorizons_cloud_scalars` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `distanthorizons_ignored_dimension_match` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `distanthorizons_render_param_inverse_reuse` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `distanthorizons_rough_surface_xz_cache` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `distanthorizons_update_queue_wait` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_direct_upload_preparation` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_lazy_completed_jobs` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_pending_upload_sum` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_section_cache_prefix_cleanup` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_serializer_lookup_snapshot` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_upload_classification` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_visible_faces_first` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `embeddium_weighted_pick_table` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `emf_block_entity_type_string` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `emf_hierarchical_id_memo` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `emf_variable_index` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `entityculling_dirty_cache_reset` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `etf_sprite_texture_id_memo` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `farmersdelight_tool_action_items` | retired on 1.21.1 | Farmer's Delight 1.3.4 for 1.21.1 replaces ToolActionIngredient and its eager constructor/packet decoder with a codec and lazy ItemAbilityIngredient.getItems(). The old repeated constructor/decoder scan no longer exists; no eager scan is added by this port. |
| `forge_plain_translation_format` | retired on 1.21.1 | ForgeI18n moved to FMLTranslations in the separate early loader module; NeoForge mod mixins cannot transform classes already loaded by that module. |
| `forge_render_layer_memo` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `forge_status_ping_channel_groups` | retired on 1.21.1 | NeoForge 21.1 networking no longer has ServerStatusPing or its per-mod channel scan. |
| `fowlplay_air_targets_loaded_only` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `fowlplay_flock_vectors` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `frame_pacing` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `fusion_chunk_layer_id` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `fusion_connection_lookups` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `geckolib_animation_hashes` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `geckolib_keyframe_locals` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `geckolib_primitive_easing` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `goblins_animation_entity_guard` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `goblins_cached_disguise_tag` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `hostilevillages_distant_generation_queue` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `hostilevillages_pending_chunk_queue` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `iceandfire_lake_guard_region` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `iceandfire_lazy_reference_lists` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `iceandfire_pixie_village_difficulty` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `immediatelyfast_known_core_shaders` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `immediatelyfast_offset_layer_prefixes` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `jei_server_item_registry` | retired on 1.21.1 | JEI 19 uses RegistryUtil.getRegistry returning the Minecraft Registry directly; the per-ingredient IPlatformRegistry wrapper allocation removed by this patch no longer exists. |
| `jei_typed_stack_cache` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `modernfix_c2me_cache_strongholds` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `moon_animation_entity_guard` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `netherdepths_reuse_enchantment_decode` | retired on 1.21.1 | Nether Depths Upgrade 3.3 uses enchantment data components and a single getHellStriderLevel call; the two NBT-to-map decodes removed by the 1.20.1 patch no longer exist. |
| `occult_bed_scan_guard` | retired on 1.21.1 | The available NeoForge 1.21.1 release, Occult alpha-2-b, has no Tendrils entity or TendrilsBedProcedure. The expensive scan introduced in the 1.20.1 Alpha 5 target is absent. |
| `oculus_dh_instance_lookup` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_empty_sampler_fast_return` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_empty_transparency_graphs` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_entity_vertex_reuse` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_phase_labels` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_primitive_buffer_affinities` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_primitive_uniform_locations` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_program_traversal` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_reuse_empty_graphs` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `oculus_shadow_edge_vectors` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `presencefootsteps_duplicate_tracking` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `radium_c2me_chunk_access` | retired on 1.21.1 | Radium 0.13.1 has no applyC2MECompat blanket disable, and getChunkBlocking already calls holder.scheduleChunkGenerationTask; the old bypass repair and option override are unnecessary. |
| `radium_c2me_player_chunk_tick` | retired on 1.21.1 | Radium 0.13.1 has neither the applyC2MECompat blanket disable nor the old world.player_chunk_tick mixin. |
| `radium_experimental_tickets_spawning` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `radium_untrack_chunk_hooks` | retired on 1.21.1 | Radium 0.13.1 removed world.player_chunk_tick.ThreadedAnvilChunkStorageMixin; its cancelled Forge tracking hooks no longer exist. |
| `relics_foreign_capability_fast_path` | retired on 1.21.1 | Relics 0.10.7.8 removed IRelicsCapability and the Forge LazyOptional capability provider targeted by this patch. |
| `ryoamiclights_block_entity_lock_skip` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `ryoamiclights_chunk_iteration` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `sakes_structure_check_once` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `scorched_sandcrab_burrow_gate` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `scuba_gear_generation_context` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `spawn_sealife_tick` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `spawn_zombified_flower_floor` | retired on 1.21.1 | Spawn 4.0.8 already checks pos.getY() > worldgenlevel.getMinBuildHeight() before its downward flower-placement loop, so the original fix is included upstream. |
| `structure_gel_lake_guard_region` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `structurify_height_cache` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `structurify_set_data_single_lookup` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `tacz_sync_collections` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `tacz_sync_scope` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `terrain_density_memo` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `terrain_final_density_reuse` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `terrain_surface_estimate_share` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `terramity_animation_entity_guard` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_backoff_nan` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_chunk_bookkeeping` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_chunk_set_version` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_collision_axes` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_entity_base_tick` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_entity_handlers` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_entity_ship_collision` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_entity_unloads` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_render_interpolation` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_sculk_vibrations` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_ship_lookups` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_spawn_distance` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_standing_probe` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_terrain_snapshot_order` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `valkyrien_weather_occlusion` | target unavailable | No author-published Minecraft 1.21.1 NeoForge build found in the recorded Modrinth/CurseForge query; original patch retained in the 1.20.1 source. |
| `vanilla_animate_tick_uniform_biome` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_block_entity_tick_state` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_block_ticking_range_memo` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_camera_fluid_memo` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_chunk_status_name_memo` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_climate_rtree_flat_bounds` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_climate_sample_repeat` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_climate_search_repeat` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_data_merge_unchanged` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_fog_color_sample_memo` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_model_bone_lookup` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_noise_column_cache` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_noise_wrap_presize` | ported | Ported; see runtime qualification and conditional compatibility notes. |
| `vanilla_poi_chunk_sections` | ported | Ported; see runtime qualification and conditional compatibility notes. |
