import type { CompositionGuidance, DetectedSubject } from "../types/composition";
import type { SceneAssessment, SceneObject } from "../types/scene";

type Detection = { boundingBox?: { originX: number; originY: number; width: number; height: number }; categories?: { categoryName?: string; score?: number }[] };
const empty: SceneAssessment = { brightness: .5, highlightClip: 0, shadowClip: 0, edgeDensity: 0, subjectContrast: .25, lightTone: "good", backgroundTone: "good", separationTone: "good", objects: [] };
export function assessScene(video: HTMLVideoElement, canvas: HTMLCanvasElement, detections: Detection[], subject: DetectedSubject | null, mirrored: boolean): SceneAssessment {
  const size = 48; canvas.width = size; canvas.height = size; const ctx = canvas.getContext("2d", { willReadFrequently: true }); if (!ctx) return empty;
  ctx.drawImage(video, 0, 0, size, size); const data = ctx.getImageData(0, 0, size, size).data; const lum: number[] = [];
  let sum = 0, highlights = 0, shadows = 0, edges = 0, subjectSum = 0, subjectN = 0, bgSum = 0, bgN = 0;
  for (let y=0;y<size;y++) for (let x=0;x<size;x++) { const i=(y*size+x)*4, l=(.2126*data[i]+.7152*data[i+1]+.0722*data[i+2])/255; lum.push(l); sum+=l; if(l>.94) highlights++; if(l<.07) shadows++; const nx=(x+.5)/size, ny=(y+.5)/size; const inSubject=subject && Math.abs(nx-subject.centerX)<subject.width*.9 && Math.abs(ny-subject.centerY)<subject.height*1.3; if(inSubject){subjectSum+=l;subjectN++;}else{bgSum+=l;bgN++;} }
  for(let y=1;y<size-1;y++) for(let x=1;x<size-1;x++){const p=lum[y*size+x], dx=Math.abs(p-lum[y*size+x+1]),dy=Math.abs(p-lum[(y+1)*size+x]);if(dx+dy>.24)edges++;}
  const brightness=sum/(size*size), highlightClip=highlights/(size*size), shadowClip=shadows/(size*size), edgeDensity=edges/((size-2)*(size-2));
  const subjectContrast=subjectN&&bgN?Math.abs(subjectSum/subjectN-bgSum/bgN):.25;
  const objects: SceneObject[]=detections.flatMap(d=>{const b=d.boundingBox,c=d.categories?.[0];if(!b||!c?.categoryName||c.categoryName==="person")return[];const scale=Math.max(video.clientWidth/video.videoWidth,video.clientHeight/video.videoHeight),rw=video.videoWidth*scale,rh=video.videoHeight*scale,cx=(rw-video.clientWidth)/2,cy=(rh-video.clientHeight)/2;let left=(b.originX*scale-cx)/video.clientWidth;const width=b.width*scale/video.clientWidth,top=(b.originY*scale-cy)/video.clientHeight,height=b.height*scale/video.clientHeight;if(mirrored)left=1-left-width;const ox=Math.max(0,Math.min(left+width,(subject?.centerX??0)+(subject?.width??0)/1.5)-Math.max(left,(subject?.centerX??0)-(subject?.width??0)/1.5)),oy=Math.max(0,Math.min(top+height,(subject?.centerY??0)+(subject?.height??0)) - Math.max(top,(subject?.centerY??0)-(subject?.height??0)));return [{label:c.categoryName,score:c.score??0,left,top,width,height,isConflict:!!subject&&ox*oy>.004}];});
  return { brightness,highlightClip,shadowClip,edgeDensity,subjectContrast,objects,lightTone:brightness<.2||highlightClip>.3?"problem":brightness<.3||highlightClip>.18?"caution":"good",backgroundTone:objects.some(o=>o.isConflict)||edgeDensity>.21?"problem":objects.length>3||edgeDensity>.14?"caution":"good",separationTone:subjectContrast<.07?"problem":subjectContrast<.13?"caution":"good" };
}
export function sceneGuidance(scene: SceneAssessment): CompositionGuidance | null {
  if(scene.brightness<.2)return{type:"READY",message:"Turn the subject toward more light",reason:"The face is too dark for a clear portrait",severity:.9};
  if(scene.highlightClip>.3)return{type:"READY",message:"Move away from the bright background",reason:"Highlights are overpowering the subject",severity:.85};
  const conflict=scene.objects.find(o=>o.isConflict);if(conflict)return{type:"READY",message:"Shift to clear the background",reason:`A ${conflict.label} is visually merging with the subject`,severity:.75};
  if(scene.backgroundTone==="problem")return{type:"READY",message:"Try a cleaner background",reason:"Too many edges are competing with the subject",severity:.65};
  if(scene.separationTone==="problem")return{type:"READY",message:"Add distance from the background",reason:"The subject and background have similar visual weight",severity:.6};
  return null;
}
