#!/usr/bin/env python3
"""Bakes the Lume main-menu chrome into HIGH-FIDELITY transparent PNGs for every
theme x glass-style combo. Rich frosted-glass look (vertical frost gradient +
bright rim + top sheen highlight + soft inner shade) matching the Figma kit, so
the in-game DrawContext blit (zero NanoVG, no crash) reads as premium as the mock.

Output: assets/lume/textures/menu/<theme>_<style>/<name>.png  +  menu/glow.png (shared)
  themes: light, dark     styles: default, fullglass, noglass

5x supersample -> LANCZOS. Run:  python tools/gen_menu.py
"""
import os, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

SS = 5
FONT_DIR = os.path.join(os.path.dirname(__file__),'..','src','main','resources','assets','lume','font')
OUT_ROOT = os.path.join(os.path.dirname(__file__),'..','src','main','resources','assets','lume','textures','menu')

def H(h): return (int(h[1:3],16),int(h[3:5],16),int(h[5:7],16))
PAL = {
 'light': dict(bg=H('#F2EBDD'),ink=H('#4A4133'),inkdim=H('#8C8170'),accent=H('#A99BC7'),accent2=H('#8E7FC0'),mintlight=H('#C9BEE0'),winbot=H('#EADFCB'),wintop=H('#F5EEE0')),
 'dark':  dict(bg=H('#221F1A'),ink=H('#F3EEE2'),inkdim=H('#B4AA97'),accent=H('#C2B4E4'),accent2=H('#9385C4'),mintlight=H('#DED4F0'),winbot=H('#2A251E'),wintop=H('#39332A')),
}

# per (style,theme): (fill_top_rgba, fill_bot_rgba, rim_rgba, rim_w, sheen_alpha)
def spec(style,theme,pal):
    wt,wb=pal['wintop'],pal['winbot']
    if style=='default':
        if theme=='light': return ((255,255,255,128),(255,255,255,80),(255,255,255,190),1.4,48)
        return ((255,255,255,72),(255,255,255,44),(255,255,255,120),1.6,52)
    if style=='fullglass':
        if theme=='light': return ((255,255,255,34),(255,255,255,12),(255,255,255,210),1.6,36)
        return ((255,255,255,28),(255,255,255,10),(255,255,255,175),1.7,34)
    # noglass -> opaque, real gradient winTop->winBot
    if theme=='light': return ((wt[0],wt[1],wt[2],255),(wb[0],wb[1],wb[2],255),(pal['ink'][0],pal['ink'][1],pal['ink'][2],34),1.0,30)
    return ((wt[0],wt[1],wt[2],255),(wb[0],wb[1],wb[2],255),(255,255,255,26),1.0,20)

def font(px): return ImageFont.truetype(os.path.join(FONT_DIR,'montserrat-bold.ttf'),px)
def new(w,h): return Image.new('RGBA',(w*SS,h*SS),(0,0,0,0))
def rrect(img,box,r,fill=None,outline=None,width=1):
    ImageDraw.Draw(img).rounded_rectangle(box,radius=r,fill=fill,outline=outline,width=max(1,int(width)))
def save(img,w,h,path): img.resize((w,h),Image.LANCZOS).save(path)
def GB(img,r): return img.filter(ImageFilter.GaussianBlur(r))

def vgrad_rgba(size,ca,cb):
    W,Hh=size; img=Image.new('RGBA',size); px=img.load()
    for y in range(Hh):
        t=y/max(1,Hh-1)
        c=tuple(int(ca[i]+(cb[i]-ca[i])*t) for i in range(4));
        for x in range(W): px[x,y]=c
    return img

def draw_surface(img,w,h,radius,style,theme,pal):
    ft,fb,rim,rw,sheen_a=spec(style,theme,pal)
    W,Hh=w*SS,h*SS; r=radius*SS
    mask=Image.new('L',(W,Hh),0); rrect(mask,[0,0,W-1,Hh-1],r,fill=255)
    # frost gradient body
    img.paste(vgrad_rgba((W,Hh),ft,fb),(0,0),mask)
    # top sheen highlight (glassy sweep across the upper third)
    if sheen_a>0:
        sh=Image.new('RGBA',(W,Hh),(0,0,0,0))
        ImageDraw.Draw(sh).rounded_rectangle([int(r*0.5),int(Hh*0.10),W-int(r*0.5),int(Hh*0.52)],radius=int(Hh*0.4),fill=(255,255,255,sheen_a))
        sh=GB(sh,Hh*0.07); sc=Image.new('RGBA',(W,Hh),(0,0,0,0)); sc.paste(sh,(0,0),mask); img.alpha_composite(sc)
    # subtle inner bottom shade for depth
    shade=Image.new('RGBA',(W,Hh),(0,0,0,0))
    ImageDraw.Draw(shade).rounded_rectangle([int(r*0.4),int(Hh*0.62),W-int(r*0.4),Hh-1],radius=int(r*0.6),fill=(0,0,0,18 if theme=='light' else 16))
    shade=GB(shade,Hh*0.06); sc2=Image.new('RGBA',(W,Hh),(0,0,0,0)); sc2.paste(shade,(0,0),mask); img.alpha_composite(sc2)
    # bright rim
    rrect(img,[0,0,W-1,Hh-1],r,outline=rim,width=rw*SS)

# ---- icons ----
def ic_gear(img,cx,cy,r,col,holecol):
    d=ImageDraw.Draw(img)
    for i in range(8):
        a=i*math.pi/4
        d.line([(cx+math.cos(a)*r*0.7,cy+math.sin(a)*r*0.7),(cx+math.cos(a)*r*1.2,cy+math.sin(a)*r*1.2)],fill=col,width=int(r*0.44))
    d.ellipse([cx-r,cy-r,cx+r,cy+r],fill=col); d.ellipse([cx-r*0.42,cy-r*0.42,cx+r*0.42,cy+r*0.42],fill=holecol)
def ic_globe(d,cx,cy,r,col):
    w=max(2,int(r*0.22)); d.ellipse([cx-r,cy-r,cx+r,cy+r],outline=col,width=w); d.line([(cx-r,cy),(cx+r,cy)],fill=col,width=w); d.ellipse([cx-r*0.45,cy-r,cx+r*0.45,cy+r],outline=col,width=w)
def ic_x(d,cx,cy,r,col):
    w=max(2,int(r*0.32)); d.line([(cx-r,cy-r),(cx+r,cy+r)],fill=col,width=w); d.line([(cx-r,cy+r),(cx+r,cy-r)],fill=col,width=w)
def ic_sun(d,cx,cy,r,col):
    w=max(2,int(r*0.24)); rr=r*0.6; d.ellipse([cx-rr,cy-rr,cx+rr,cy+rr],outline=col,width=w)
    for i in range(8):
        a=i*math.pi/4; d.line([(cx+math.cos(a)*r*0.95,cy+math.sin(a)*r*0.95),(cx+math.cos(a)*r*1.3,cy+math.sin(a)*r*1.3)],fill=col,width=w)
def ic_moon(img,cx,cy,r,col):
    m=Image.new('L',img.size,0); md=ImageDraw.Draw(m); md.ellipse([cx-r,cy-r,cx+r,cy+r],fill=255); md.ellipse([cx-r*0.3,cy-r*1.05,cx+r*1.7,cy+r*0.95],fill=0)
    img.paste(Image.new('RGBA',img.size,col),(0,0),m)
def ic_dots(d,cx,cy,r,col):
    w=max(2,int(r*0.22)); rr=r*0.28
    for (x,y) in [(cx+r*0.55,cy-r*0.75),(cx+r*0.95,cy+r*0.05),(cx-r*0.85,cy-r*0.1),(cx-r*0.35,cy+r*0.85)]:
        d.ellipse([x-rr,y-rr,x+rr,y+rr],outline=col,width=w)

# ---- glass star ----
ST_T=[(50,18),(82,50),(50,82),(18,50)]; ST_C=[(58,42),(58,58),(42,58),(42,42)]
def _qb(p0,c,p1,steps=26):
    o=[]
    for i in range(steps+1):
        t=i/steps; mt=1-t; o.append((mt*mt*p0[0]+2*mt*t*c[0]+t*t*p1[0],mt*mt*p0[1]+2*mt*t*c[1]+t*t*p1[1]))
    return o
def star_poly(ox,oy,sc):
    T=[(ox+x*sc,oy+y*sc) for x,y in ST_T]; C=[(ox+x*sc,oy+y*sc) for x,y in ST_C]
    return _qb(T[0],C[0],T[1])+_qb(T[1],C[1],T[2])+_qb(T[2],C[2],T[3])+_qb(T[3],C[3],T[0])
def vgrad_rgb(size,ca,cb):
    W,Hh=size; img=Image.new('RGBA',size); px=img.load()
    for y in range(Hh):
        t=y/max(1,Hh-1); c=(int(ca[0]+(cb[0]-ca[0])*t),int(ca[1]+(cb[1]-ca[1])*t),int(ca[2]+(cb[2]-ca[2])*t),255)
        for x in range(W): px[x,y]=c
    return img
def draw_glass_star(img,ox,oy,size,pal):
    sc=size*SS/100.0; poly=star_poly(ox*SS,oy*SS,sc)
    glow=Image.new('RGBA',img.size,(0,0,0,0)); ImageDraw.Draw(glow).polygon(poly,fill=(pal['accent'][0],pal['accent'][1],pal['accent'][2],160))
    img.alpha_composite(GB(glow,size*SS*0.13))
    mask=Image.new('L',img.size,0); ImageDraw.Draw(mask).polygon(poly,fill=225)
    img.paste(vgrad_rgb(img.size,(240,235,250),pal['accent']),(0,0),mask)
    ImageDraw.Draw(img).line(poly+[poly[0]],fill=(255,255,255,235),width=max(2,int(size*SS*0.022)))

def hgrad_text(word,f,ty,ca,cb,W,Hh):
    m=Image.new('L',(W,Hh),0); ImageDraw.Draw(m).text((0,ty),word,font=f,fill=255)
    g=Image.new('RGBA',(W,Hh)); px=g.load()
    for x in range(W):
        t=x/max(1,W-1); c=(int(ca[0]+(cb[0]-ca[0])*t),int(ca[1]+(cb[1]-ca[1])*t),int(ca[2]+(cb[2]-ca[2])*t),255)
        for y in range(Hh): px[x,y]=c
    return g,m

def label(img,text,w,h,size,col):
    d=ImageDraw.Draw(img); f=font(size*SS); bb=d.textbbox((0,0),text,font=f)
    d.text(((w*SS-(bb[2]-bb[0]))//2-bb[0],(h*SS-(bb[3]-bb[1]))//2-bb[1]),text,font=f,fill=col)

def build_set(theme,style):
    pal=PAL[theme]; key=f'{theme}_{style}'; outdir=os.path.join(OUT_ROOT,key); os.makedirs(outdir,exist_ok=True)
    ink=pal['ink']; inkc=(ink[0],ink[1],ink[2],255); bg=pal['bg']; holecol=(bg[0],bg[1],bg[2],255)

    img=new(210,32); draw_glass_star(img,3,4,24,pal)
    d=ImageDraw.Draw(img); f=font(20*SS); asc,desc=f.getmetrics(); ty=(32*SS-(asc+desc))//2
    tx=int((3+24+7)*SS); d.text((tx,ty),'LUME',font=f,fill=inkc); lw=d.textbbox((0,0),'LUME',font=f)[2]; vx=tx+lw+6*SS
    ww=d.textbbox((0,0),'VISUALS',font=f)[2]; g,m=hgrad_text('VISUALS',f,ty,pal['accent'],pal['mintlight'],ww+8*SS,32*SS)
    img.paste(g,(int(vx),0),m); save(img,210,32,os.path.join(outdir,'logo.png'))

    for nm,lbl in [('singleplayer','Singleplayer'),('multiplayer','Multiplayer')]:
        img=new(200,20); draw_surface(img,200,20,6,style,theme,pal); label(img,lbl,200,20,11,inkc); save(img,200,20,os.path.join(outdir,nm+'.png'))

    img=new(98,20); draw_surface(img,98,20,6,style,theme,pal); d=ImageDraw.Draw(img)
    d.line([(49*SS,4*SS),(49*SS,16*SS)],fill=(ink[0],ink[1],ink[2],90),width=max(1,SS//2))
    ic_gear(img,24*SS,10*SS,3.0*SS,inkc,holecol); ic_globe(ImageDraw.Draw(img),73*SS,10*SS,3.1*SS,inkc); save(img,98,20,os.path.join(outdir,'box_options_language.png'))
    img=new(44,20); draw_surface(img,44,20,6,style,theme,pal); ic_x(ImageDraw.Draw(img),22*SS,10*SS,4.2*SS,inkc); save(img,44,20,os.path.join(outdir,'box_quit.png'))

    # Glyph-ONLY sprites (transparent bg) — the SDF shader now draws the button background live
    # (fill + contour glow + lift), so these just blit the icon on top of it. Was: background
    # baked into the same PNG as the glyph.
    def icon_btn(nm,drawer):
        img=new(24,24); drawer(img); save(img,24,24,os.path.join(outdir,nm+'.png'))
    icon_btn('ic_theme', lambda im:(ic_sun(ImageDraw.Draw(im),12*SS,12*SS,5*SS,inkc) if theme=='light' else ic_moon(im,12*SS,12*SS,5*SS,inkc)))
    icon_btn('ic_colors',lambda im: ic_dots(ImageDraw.Draw(im),12*SS,12*SS,6*SS,inkc))
    icon_btn('ic_gear',  lambda im: ic_gear(im,12*SS,12*SS,4.2*SS,inkc,holecol))
    icon_btn('ic_globe', lambda im: ic_globe(ImageDraw.Draw(im),12*SS,12*SS,7*SS,inkc))
    icon_btn('ic_x',     lambda im: ic_x(ImageDraw.Draw(im),12*SS,12*SS,7*SS,inkc))
    icon_btn('ic_menu',  lambda im: draw_glass_star(im,4,4,16,pal))

    img=new(96,24); draw_surface(img,96,24,7,style,theme,pal); save(img,96,24,os.path.join(outdir,'pill.png'))
    img=new(108,24); draw_surface(img,108,24,7,style,theme,pal); save(img,108,24,os.path.join(outdir,'account.png'))
    img=new(150,20); draw_surface(img,150,20,6,style,theme,pal); save(img,150,20,os.path.join(outdir,'row.png'))
    return key

def build_glow():
    # shared soft radial glow (white, tinted at blit) with transparent margin
    N=256; img=Image.new('RGBA',(N,N),(0,0,0,0)); px=img.load(); c=N/2
    for y in range(N):
        for x in range(N):
            d=math.hypot(x-c,y-c)/c; a=max(0.0,1.0-d); a=a*a
            px[x,y]=(255,255,255,int(a*255))
    img.save(os.path.join(OUT_ROOT,'glow.png'))

if __name__=='__main__':
    os.makedirs(OUT_ROOT,exist_ok=True); build_glow()
    made=[build_set(t,s) for t in ('light','dark') for s in ('default','fullglass','noglass')]
    print('built:',', '.join(made),'+ glow.png'); print('->',os.path.abspath(OUT_ROOT))
