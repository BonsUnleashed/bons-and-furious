# Bons Pure Optimizations (sakes_structure_check_once): one pass per player, upstream call order preserved
tag @s remove bons_pure_ss_in
tag @s remove bons_pure_ss_gen
execute if predicate sakes_structures:is_in_protected_structure run tag @s add bons_pure_ss_in
execute if entity @s[tag=bons_pure_ss_in] if entity @e[type=sakes_structures:prevention_field_generator,distance=..32] run tag @s add bons_pure_ss_gen
execute if entity @s[tag=bons_pure_ss_gen] run ss-canbuild @s false
execute if entity @s[tag=bons_pure_ss_in,tag=!bons_pure_ss_gen] run ss-canbuild @s true
execute if entity @s[tag=!bons_pure_ss_in] run ss-canbuild @s true
execute if entity @e[type=sakes_structures:prevention_field_generator,distance=..5] run ss-canbuild @s false
tag @s remove bons_pure_ss_in
tag @s remove bons_pure_ss_gen