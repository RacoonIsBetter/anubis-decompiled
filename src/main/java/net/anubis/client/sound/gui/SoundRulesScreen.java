// Ported from Donut Client (https://github.com/Raindots/donut-client), MIT License,
// Copyright (c) 2026 Donut Client contributors. See licenses/donut-client-MIT.txt.
package net.anubis.client.sound.gui;

import net.anubis.client.sound.SoundDetectorConfig;
import net.anubis.client.sound.SoundDetectorSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Guided rules/expected-area list. Complex typed condition groups remain editable in Advanced JSON. */
public final class SoundRulesScreen extends Screen {
    private final Screen parent;
    private final SoundDetectorConfig config;
    private final Runnable save;
    private boolean areas;
    private int page;
    private String status="Rules combine their filters with AND. Empty filters match any source.";
    public SoundRulesScreen(Screen parent,SoundDetectorConfig config,Runnable save){super(Text.literal("Sound rules and expected areas"));this.parent=parent;this.config=config;this.save=save;}
    private int perPage(){return Math.max(1,(height-120)/28);}
    @Override protected void init(){
        addDrawableChild(ButtonWidget.builder(Text.literal(areas?"Show rules":"Show expected areas"),b->{areas=!areas;page=0;clearAndInit();}).dimensions(14,34,150,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(areas?"Add current area":"Add rule"),b->{
            if(areas){if(config.soundDetector.zones.size()>=64){status="Maximum 64 areas.";return;}var z=new SoundDetectorSettings.Zone();if(client.player!=null){z.x=client.player.getX();z.y=client.player.getY();z.z=client.player.getZ();}if(client.world!=null)z.dimension=client.world.getRegistryKey().getValue().toString();config.soundDetector.zones.add(z);save.run();client.setScreen(new SoundRuleFormScreen(this,config,save,z));}
            else{if(config.soundDetector.rules.size()>=64){status="Maximum 64 rules.";return;}var r=new SoundDetectorSettings.Rule();config.soundDetector.rules.add(r);save.run();client.setScreen(new SoundRuleFormScreen(this,config,save,r));}
        }).dimensions(170,34,135,20).build());
        int count=areas?config.soundDetector.zones.size():config.soundDetector.rules.size();page=Math.clamp(page,0,Math.max(0,(count-1)/perPage()));
        for(int n=page*perPage();n<Math.min(count,(page+1)*perPage());n++){
            int index=n,y=64+(n-page*perPage())*28;
            String name=areas?config.soundDetector.zones.get(n).name:config.soundDetector.rules.get(n).name;
            addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(name,width-240)),b->{Object entry=areas?config.soundDetector.zones.get(index):config.soundDetector.rules.get(index);client.setScreen(new SoundRuleFormScreen(this,config,save,entry));}).dimensions(14,y,Math.max(100,width-210),22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(areas?(config.soundDetector.zones.get(n).mute?"Muted":"Observe"):(config.soundDetector.rules.get(n).enabled?"Enabled":"Disabled")),b->{if(areas){var z=config.soundDetector.zones.get(index);z.mute=!z.mute;}else{var r=config.soundDetector.rules.get(index);r.enabled=!r.enabled;}save.run();clearAndInit();}).dimensions(width-190,y,90,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Delete"),b->{if(areas)config.soundDetector.zones.remove(index);else config.soundDetector.rules.remove(index);save.run();clearAndInit();}).dimensions(width-96,y,82,22).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Previous"),b->{if(page>0){page--;clearAndInit();}}).dimensions(14,height-30,90,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next"),b->{if((page+1)*perPage()<count){page++;clearAndInit();}}).dimensions(108,height-30,90,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Advanced JSON"),b->client.setScreen(new SoundSettingsJsonScreen(this,config,save))).dimensions(width-224,height-30,116,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"),b->close()).dimensions(width-104,height-30,90,20).build());
    }
    @Override public void render(DrawContext g,int mx,int my,float delta){g.fill(0,0,width,height,0xF0101722);g.drawCenteredTextWithShadow(textRenderer,areas?"Expected sound areas":"Custom sound rules",width/2,12,0xFFF1F5FB);g.drawText(textRenderer,textRenderer.trimToWidth(status,width-28),14,height-46,0xFFB3C8DB,false);super.render(g,mx,my,delta);}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
