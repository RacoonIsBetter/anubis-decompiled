package com.example.client.mixin;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.render.item.ItemRenderState.LayerRenderState;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(LayerRenderState.class)
public interface AccessorHandItemLayer {
   @Accessor("transform")
   Transformation anubis$handTransform();

   @Accessor("vertices")
   Supplier<Vector3fc[]> anubis$handExtents();
}
