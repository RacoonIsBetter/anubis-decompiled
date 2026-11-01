package net.anubis.client.easyplace.schematic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public record SchematicBlockState(BlockState state, Identifier blockId, Map<String, String> properties) {
   public static SchematicBlockState of(BlockState state) {
      Map<String, String> properties = new LinkedHashMap<>();
      state.getEntries().forEach((property, value) -> properties.put(property.getName(), value.toString()));
      return new SchematicBlockState(state, Registries.BLOCK.getId(state.getBlock()), Collections.unmodifiableMap(properties));
   }

   public String debugString() {
      if (this.properties.isEmpty()) {
         return this.blockId.toString();
      }

      StringBuilder result = new StringBuilder(this.blockId.toString()).append('[');
      this.properties.forEach((name, value) -> result.append(name).append('=').append(value).append(','));
      result.setCharAt(result.length() - 1, ']');
      return result.toString();
   }
}
