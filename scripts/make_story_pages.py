"""스토리 그림 3장 — 대표님이 찍어 보낸 사진(Plan/story/story1~3.jpg)에서 종이만 반듯하게 펴고 밝기를 고르게 한다 (2026-09-27).
결과: app/src/main/res/drawable-nodpi/story_1~3.jpg
사용법: python scripts/make_story_pages.py <임시 폴더>  (임시 폴더에 prev/ 가 있어야 함)
"""
import sys, numpy as np
from PIL import Image, ImageOps, ImageEnhance
S=sys.argv[1]
def coeffs(dst, src):
    A=[];B=[]
    for (x,y),(X,Y) in zip(dst,src):
        A.append([x,y,1,0,0,0,-X*x,-X*y]); B.append(X)
        A.append([0,0,0,x,y,1,-Y*x,-Y*y]); B.append(Y)
    return np.linalg.solve(np.array(A,float),np.array(B,float))
C={1:[(524,22),(1026,36),(1021,719),(531,719)],
   2:[(450,-10),(862,76),(944,719),(458,719)],
   3:[(492,38),(1002,132),(960,719),(446,719)]}
for i,c in C.items():
    im=Image.open(f'Plan/story/story{i}.jpg').convert('RGB')
    W,H=720,1000
    out=im.transform((W,H),Image.PERSPECTIVE,coeffs([(0,0),(W,0),(W,H),(0,H)],c),Image.BICUBIC)
    out.save(f"{S}/prev/st{i}.png")
from PIL import ImageFilter
for i in C:
    im=Image.open(f'{S}/prev/st{i}.png').convert('RGB')
    a=np.asarray(im).astype(float)
    # 종이 밖(검은 빈칸)은 근처 종이색으로
    blackm=a.sum(axis=2)<30
    g=np.asarray(im.convert('L').filter(ImageFilter.MaxFilter(15)).filter(ImageFilter.GaussianBlur(40))).astype(float)
    g=np.maximum(g,60)
    flat=np.clip(a/g[...,None]*245,0,255)
    flat[blackm]=245
    out=Image.fromarray(flat.astype(np.uint8))
    out=ImageEnhance.Color(out).enhance(1.25)
    out=ImageEnhance.Contrast(out).enhance(1.1)
    out.save(f'app/src/main/res/drawable-nodpi/story_{i}.jpg',quality=88)
    out.save(f'{S}/prev/sf{i}.png')
