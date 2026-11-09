// Ported from Donut Client (https://github.com/Raindots/donut-client), MIT License,
// Copyright (c) 2026 Donut Client contributors. See licenses/donut-client-MIT.txt.
package net.anubis.client.sound.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.anubis.client.sound.SoundDetectorConfig;
import net.anubis.client.sound.SoundDetectorSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

/** Explicit advanced import/export editor. Imported values are data, never executable expressions. */
public final class SoundSettingsJsonScreen extends Screen {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final Screen parent;
    private final SoundDetectorConfig config;
    private final Runnable save;
    private EditBoxWidget editor;
    private String draft,status="Advanced: edit rules, source filters, profiles, areas, or safe text templates.";
    public SoundSettingsJsonScreen(Screen parent,SoundDetectorConfig config,Runnable save){super(Text.literal("Advanced sound preferences"));this.parent=parent;this.config=config;this.save=save;draft=JSON.toJson(config.soundDetector);}
    @Override protected void init(){
        editor=addDrawableChild(EditBoxWidget.builder().x(14).y(48).hasBackground(true).hasOverlay(true).build(textRenderer,width-28,Math.max(40,height-106),Text.literal("Sound preferences JSON")));
        editor.setMaxLength(60000);editor.setText(draft);editor.setChangeListener(v->draft=v);
        int bw=Math.min(120,(width-40)/4),left=(width-(bw+4)*4+4)/2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Apply"),b->apply()).dimensions(left,height-30,bw,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Copy"),b->{client.keyboard.setClipboard(draft);status="Copied preferences to clipboard.";}).dimensions(left+bw+4,height-30,bw,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Paste"),b->{String clipboard=client.keyboard.getClipboard();if(clipboard.length()<=60000){editor.setText(clipboard);status="Review, then choose Apply.";}else status="Import is too large (maximum 60,000 characters).";}).dimensions(left+2*(bw+4),height-30,bw,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"),b->close()).dimensions(left+3*(bw+4),height-30,bw,20).build());
    }
    private void apply(){
        try{
            if(draft.length()>60000)throw new IllegalArgumentException("Import too large");
            // Bound nesting before Gson traverses attacker-controlled data.
            int depth=0,max=0;boolean quoted=false,escape=false;
            for(char c:draft.toCharArray()){
                if(quoted){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quoted=false;continue;}
                if(c=='"')quoted=true;else if(c=='{'||c=='['){depth++;max=Math.max(depth,max);}else if(c=='}'||c==']')depth--;
                if(depth<0||max>16)throw new IllegalArgumentException("Maximum nesting is 16");
            }
            var parsed=JsonParser.parseString(draft);if(!parsed.isJsonObject())throw new IllegalArgumentException("Expected an object");
            SoundDetectorSettings result=JSON.fromJson(parsed,SoundDetectorSettings.class);if(result==null)throw new IllegalArgumentException("Empty settings");
            result.sanitize();config.soundDetector=result;save.run();status="Applied validated preferences.";
        }catch(RuntimeException e){status="Could not apply: "+e.getMessage();}
    }
    @Override public void render(DrawContext g,int mx,int my,float delta){g.fill(0,0,width,height,0xF0101722);g.drawCenteredTextWithShadow(textRenderer,"Advanced sound preferences",width/2,12,0xFFF1F5FB);g.drawText(textRenderer,textRenderer.trimToWidth(status,width-28),14,31,0xFFBCCFE3,false);super.render(g,mx,my,delta);}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
