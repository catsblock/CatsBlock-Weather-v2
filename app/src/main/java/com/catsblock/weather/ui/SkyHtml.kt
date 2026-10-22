package com.catsblock.weather.ui

import com.catsblock.weather.i18n.tr
import org.json.JSONObject
import java.util.Locale

internal object SkyHtml {

    private val keys = listOf("Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune")

    private fun fmt(v: Double) = String.format(Locale.US, "%.5f", v)

    fun build(lat: Double, lon: Double, decl: Double): String {
        val names = JSONObject().also { o -> keys.forEach { o.put(it, tr(it)) } }.toString()
        return PAGE.replace("__LAT__", fmt(lat))
            .replace("__LON__", fmt(lon))
            .replace("__DECL__", fmt(decl))
            .replace("__NAMES__", names)
    }

    private val PAGE = """
<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>html,body{margin:0;height:100%;background:transparent;overflow:hidden}canvas{display:block;position:absolute;left:0;top:0}.l{position:absolute;left:0;top:0;color:#fff;font:600 12px sans-serif;text-shadow:0 0 4px #000,0 0 2px #000;white-space:nowrap;pointer-events:none;will-change:transform;display:none}.g{color:#c9b8ff;font-weight:400;font-size:11px}</style></head><body>
<script src="https://cdn.jsdelivr.net/npm/three@0.147.0/build/three.min.js"></script>
<script>
(function(){
var LAT=__LAT__,LON=__LON__,DECL=__DECL__,NAMES=__NAMES__,RAD=Math.PI/180;
var KL=true,KG=true,KT=true,lines=null,AU=149597870.7;
var CEL='https://cdn.jsdelivr.net/gh/ofrohn/d3-celestial@master/data/';
var renderer=new THREE.WebGLRenderer({antialias:true,alpha:true});
renderer.setClearColor(0x000000,0);
renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,2));
renderer.setSize(innerWidth,innerHeight);
document.body.appendChild(renderer.domElement);
var scene=new THREE.Scene();
var camera=new THREE.PerspectiveCamera(64,innerWidth/innerHeight,.1,1000);
var sky=new THREE.Group();sky.matrixAutoUpdate=false;scene.add(sky);
var labels=[];
function eq(ra,dec,r){var a=ra*RAD,d=dec*RAD;return new THREE.Vector3(r*Math.cos(d)*Math.cos(a),r*Math.cos(d)*Math.sin(a),r*Math.sin(d));}
function label(text,pos,cls,dy,on){var e=document.createElement('div');e.className='l '+(cls||'');e.textContent=text;document.body.appendChild(e);var o={e:e,p:pos,dy:dy,on:on};labels.push(o);return o;}
function col(bv){var t=Math.max(0,Math.min(1,(bv+.3)/2.1)),a=t<.5?[.62,.75,1]:[1,.98,.9],b=t<.5?[1,.98,.9]:[1,.6,.4],k=t<.5?t*2:(t-.5)*2;return a.map(function(x,i){return x+(b[i]-x)*k;});}
function upd(){
var d=Date.now()/864e5-10957.5,th=((280.46061837+360.98564736629*d+LON)%360)*RAD,ph=LAT*RAD,cT=Math.cos(th),sT=Math.sin(th),cP=Math.cos(ph),sP=Math.sin(ph),cD=Math.cos(DECL*RAD),sD=Math.sin(DECL*RAD);
var E=[-sT,cT,0],N=[-sP*cT,-sP*sT,cP],U=[cP*cT,cP*sT,sP];
var E2=[E[0]*cD-N[0]*sD,E[1]*cD-N[1]*sD,E[2]*cD-N[2]*sD],N2=[E[0]*sD+N[0]*cD,E[1]*sD+N[1]*cD,E[2]*sD+N[2]*cD];
sky.matrix.set(E2[0],E2[1],E2[2],0,N2[0],N2[1],N2[2],0,U[0],U[1],U[2],0,0,0,0,1);
sky.matrixWorldNeedsUpdate=true;}
var starMat=new THREE.ShaderMaterial({transparent:true,depthWrite:false,uniforms:{k:{value:renderer.getPixelRatio()}},
vertexShader:'attribute float s;attribute vec3 c;varying vec3 vc;uniform float k;void main(){vc=c;gl_PointSize=s*k;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}',
fragmentShader:'varying vec3 vc;void main(){float d=length(gl_PointCoord-.5)*2.;gl_FragColor=vec4(vc,smoothstep(1.,0.,d));}'});
fetch(CEL+'stars.6.json').then(function(r){return r.json();}).then(function(g){
var F=g.features,n=F.length,P=new Float32Array(n*3),C=new Float32Array(n*3),S=new Float32Array(n);
for(var i=0;i<n;i++){var f=F[i],c=f.geometry.coordinates,m=+f.properties.mag,v=eq(c[0],c[1],500),k=col(parseFloat(f.properties.bv)||.6);
P[i*3]=v.x;P[i*3+1]=v.y;P[i*3+2]=v.z;C[i*3]=k[0];C[i*3+1]=k[1];C[i*3+2]=k[2];S[i]=Math.max(1.8,Math.min(14,9.5-1.4*m));}
var geo=new THREE.BufferGeometry();geo.setAttribute('position',new THREE.BufferAttribute(P,3));geo.setAttribute('c',new THREE.BufferAttribute(C,3));geo.setAttribute('s',new THREE.BufferAttribute(S,1));
var pts=new THREE.Points(geo,starMat);pts.frustumCulled=false;sky.add(pts);}).catch(function(){});
fetch(CEL+'constellations.lines.json').then(function(r){return r.json();}).then(function(g){
var p=[];g.features.forEach(function(f){var G=f.geometry,L=G.type=='MultiLineString'?G.coordinates:[G.coordinates];L.forEach(function(l){for(var i=0;i<l.length-1;i++){var a=eq(l[i][0],l[i][1],490),b=eq(l[i+1][0],l[i+1][1],490);p.push(a.x,a.y,a.z,b.x,b.y,b.z);}});});
var geo=new THREE.BufferGeometry();geo.setAttribute('position',new THREE.Float32BufferAttribute(p,3));
lines=new THREE.LineSegments(geo,new THREE.LineBasicMaterial({color:0x6fa8ff,transparent:true,opacity:.35,depthWrite:false}));lines.frustumCulled=false;lines.visible=KL;sky.add(lines);}).catch(function(){});
var gal=new THREE.Group();sky.add(gal);
function gtex(){var c=document.createElement('canvas');c.width=128;c.height=64;var x=c.getContext('2d');x.translate(64,32);x.scale(1,.5);var g=x.createRadialGradient(0,0,0,0,0,62);g.addColorStop(0,'rgba(255,245,230,1)');g.addColorStop(.25,'rgba(200,190,255,.55)');g.addColorStop(1,'rgba(120,110,200,0)');x.fillStyle=g;x.beginPath();x.arc(0,0,62,0,7);x.fill();return new THREE.CanvasTexture(c);}
var GT=gtex(),GAL=['g','gg','s','s0','sd','e','i'];
fetch(CEL+'dsos.bright.json').then(function(r){return r.json();}).then(function(g){
g.features.forEach(function(f){var p=f.properties;if(GAL.indexOf(p.type)<0)return;
var c=f.geometry.coordinates,mag=+p.mag,dim=parseFloat(p.dim)||10,ang=Math.min(7,2+dim/40);
var m=new THREE.SpriteMaterial({map:GT,transparent:true,depthWrite:false,blending:THREE.AdditiveBlending,opacity:Math.max(.4,Math.min(1,1.2-.1*(mag-3))),rotation:(c[0]*7)%3});
var s=new THREE.Sprite(m),pos=eq(c[0],c[1],450),sz=2*450*Math.tan(ang*RAD);
s.position.copy(pos);s.scale.set(sz,sz/2,1);gal.add(s);
if(mag<=9.5&&p.desig)label(p.desig,pos,'g',10,function(){return KT&&KG;});});}).catch(function(){});
function solid(c){var cv=document.createElement('canvas');cv.width=cv.height=2;var x=cv.getContext('2d');x.fillStyle=c;x.fillRect(0,0,2,2);return new THREE.CanvasTexture(cv);}
var moonTex=solid('#9a9a9a');
new THREE.TextureLoader().setCrossOrigin('anonymous').load('https://cdn.jsdelivr.net/gh/mrdoob/three.js@r147/examples/textures/planets/moon_1024.jpg',function(t){moonTex=t;if(moonMat)moonMat.uniforms.map.value=t;});
var moonMat=null;
var NOISE='float h(vec3 p){return fract(sin(dot(p,vec3(12.9898,78.233,37.719)))*43758.5453);}float vn(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(mix(h(i),h(i+vec3(1,0,0)),f.x),mix(h(i+vec3(0,1,0)),h(i+vec3(1,1,0)),f.x),f.y),mix(mix(h(i+vec3(0,0,1)),h(i+vec3(1,0,1)),f.x),mix(h(i+vec3(0,1,1)),h(i+vec3(1,1,1)),f.x),f.y),f.z);}';
var VERT='varying vec3 n;void main(){n=normal;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}';
var PFRAG='precision highp float;varying vec3 n;uniform vec3 L,c1,c2;uniform float band;'+NOISE+'void main(){vec3 q=normalize(n);float b=band>0.?.5+.5*sin(q.z*band+(vn(q*5.)-.5)*3.):vn(q*7.)*.6+vn(q*23.)*.4;vec3 c=mix(c1,c2,b);float l=smoothstep(-.04,.22,dot(q,L));gl_FragColor=vec4(c*(.035+.965*l),1.);}';
var MFRAG='precision highp float;varying vec3 n;uniform sampler2D map;uniform vec3 L,U,Ug,S;uniform float sm,ru,rp,on;void main(){vec3 q=normalize(n);vec3 f=-U;vec3 up=normalize(vec3(0.,0.,1.)-dot(vec3(0.,0.,1.),f)*f);vec3 rt=cross(up,f);float a=dot(q,rt),b=dot(q,up),c=dot(q,f);vec2 uv=vec2(atan(a,c)/6.2831853+.5,asin(clamp(b,-1.,1.))/3.1415927+.5);vec3 col=texture2D(map,uv).rgb;float l=smoothstep(-.03,.18,dot(q,L));vec3 o=col*(.04+.96*l);if(on>.5){vec3 pg=normalize(Ug+(q-dot(q,U)*U)*sm);float d=acos(clamp(dot(pg,S),-1.,1.));float iu=1.-smoothstep(ru*.94,ru*1.03,d);float ip=1.-smoothstep(ru,rp,d);float depth=clamp(1.-d/ru,0.,1.);vec3 uc=mix(vec3(.86,.38,.15),vec3(.3,.07,.03),depth);o=mix(o*(1.-.3*ip),o*uc*1.7,iu);}gl_FragColor=vec4(o,1.);}';
var RK={Moon:1737.4,Mercury:2439.7,Venus:6051.8,Mars:3389.5,Jupiter:69911,Saturn:58232,Uranus:25362,Neptune:24622};
var PAL={Mercury:[[.56,.54,.5],[.36,.34,.32],0],Venus:[[.93,.85,.66],[.82,.7,.48],9],Mars:[[.8,.39,.21],[.52,.24,.13],0],Jupiter:[[.86,.71,.53],[.58,.38,.27],28],Saturn:[[.9,.8,.58],[.74,.62,.42],22],Uranus:[[.62,.86,.88],[.5,.78,.83],5],Neptune:[[.26,.42,.88],[.17,.3,.7],8]};
var bodies=new THREE.Group();sky.add(bodies);var bodyLabs=[];
window.setBodies=function(D){
while(bodies.children.length)bodies.remove(bodies.children[0]);
bodyLabs.forEach(function(l){document.body.removeChild(l.e);labels.splice(labels.indexOf(l),1);});bodyLabs=[];
var sun=null;D.bodies.forEach(function(b){if(b.id=='Sun')sun=b;});if(!sun)return;
var SV=eq(sun.ra,sun.dec,sun.d),S=eq(sun.ra+180,-sun.dec,1);
D.bodies.forEach(function(b){if(b.id=='Sun')return;
var u=eq(b.ra,b.dec,1),BV=eq(b.ra,b.dec,b.d),L=SV.clone().sub(BV).normalize();
var R=RK[b.id],ang=R/(b.d*AU),dd=Math.max(.3,.7+.7*Math.log10(ang*206265)),rad=300*Math.tan(dd*RAD),mat;
if(b.id=='Moon'){
var Ug=D.geo?eq(D.geo.ra,D.geo.dec,1):u,pm=Math.asin(6378.14/(b.d*AU)),ps=8.794/3600*RAD/sun.d,ss=959.63/3600*RAD/sun.d,ru=1.02*(pm+ps-ss),rp=1.02*(pm+ps+ss),sm=Math.sin(ang);
var on=Math.acos(Math.max(-1,Math.min(1,Ug.dot(S))))<rp+sm?1:0;
mat=new THREE.ShaderMaterial({uniforms:{map:{value:moonTex},L:{value:L},U:{value:u},Ug:{value:Ug},S:{value:S},sm:{value:sm},ru:{value:ru},rp:{value:rp},on:{value:on}},vertexShader:VERT,fragmentShader:MFRAG});moonMat=mat;
}else{var P=PAL[b.id];mat=new THREE.ShaderMaterial({uniforms:{L:{value:L},c1:{value:new THREE.Vector3(P[0][0],P[0][1],P[0][2])},c2:{value:new THREE.Vector3(P[1][0],P[1][1],P[1][2])},band:{value:P[2]}},vertexShader:VERT,fragmentShader:PFRAG});}
var m=new THREE.Mesh(new THREE.SphereGeometry(rad,48,32),mat);m.position.copy(eq(b.ra,b.dec,300));bodies.add(m);
if(b.id=='Saturn'){var rg=new THREE.Mesh(new THREE.RingGeometry(1.3,2.3,96),new THREE.MeshBasicMaterial({color:0xcdb88a,side:THREE.DoubleSide,transparent:true,opacity:.78}));rg.scale.setScalar(rad);rg.position.copy(m.position);rg.quaternion.setFromUnitVectors(new THREE.Vector3(0,0,1),eq(40.589,83.537,1));bodies.add(rg);}
var px=Math.tan(dd*RAD)/Math.tan(32*RAD)*(innerHeight/2)+4;
bodyLabs.push(label(NAMES[b.id]||b.id,m.position,'',px,function(){return KT;}));
});};
window.setPose=function(a,b,c,d,e,f,g,h,i){camera.matrix.set(a,b,c,0,d,e,f,0,g,h,i,0,0,0,0,1);camera.matrix.decompose(camera.position,camera.quaternion,camera.scale);};
window.setLayer=function(n,v){if(n=='lines'){KL=v;if(lines)lines.visible=v;}if(n=='galaxies'){KG=v;gal.visible=v;}if(n=='labels')KT=v;};
var tmp=new THREE.Vector3();
function lab(){var w=innerWidth/2,h=innerHeight/2;
labels.forEach(function(L){tmp.copy(L.p).applyMatrix4(sky.matrix).project(camera);
var vis=L.on()&&tmp.z<1&&Math.abs(tmp.x)<1.1&&Math.abs(tmp.y)<1.1;
L.e.style.display=vis?'block':'none';
if(vis)L.e.style.transform='translate('+(tmp.x*w+w)+'px,'+(h-tmp.y*h+L.dy)+'px) translateX(-50%)';});}
addEventListener('resize',function(){renderer.setSize(innerWidth,innerHeight);camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();});
var lt=0;
function frame(now){upd();renderer.render(scene,camera);if(now-lt>50){lt=now;lab();}requestAnimationFrame(frame);}
requestAnimationFrame(frame);
})();
</script></body></html>
"""
}
