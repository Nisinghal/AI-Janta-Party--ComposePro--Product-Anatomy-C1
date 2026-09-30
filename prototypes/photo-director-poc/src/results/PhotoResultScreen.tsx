import { Download, RotateCcw } from "lucide-react";
export default function PhotoResultScreen({ image, onRetake }: { image: string; onRetake: () => void }) {
  return <main className="result-screen"><div className="result-photo"><img src={image} alt="Captured portrait" /></div><div className="result-actions"><button type="button" onClick={onRetake}><RotateCcw size={20} /> Retake</button><a href={image} download={`photo-director-${Date.now()}.jpg`}><Download size={20} /> Save photo</a></div></main>;
}
