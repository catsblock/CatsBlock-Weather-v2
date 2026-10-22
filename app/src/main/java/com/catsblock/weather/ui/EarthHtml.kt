package com.catsblock.weather.ui

import java.util.Locale

internal object EarthHtml {

    fun build(lat: Double, lon: Double, interactive: Boolean, loading: String): String =
        PAGE.replace("__LAT__", String.format(Locale.US, "%.5f", lat))
            .replace("__LON__", String.format(Locale.US, "%.5f", lon))
            .replace("__INTER__", if (interactive) "true" else "false")
            .replace("__LOADING__", loading)

    private val PAGE = """
<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>html,body{margin:0;height:100%;background:transparent;overflow:hidden}canvas{display:block;touch-action:none}#m{position:absolute;left:0;right:0;top:48%;text-align:center;color:#fff;font:14px sans-serif;opacity:.85;pointer-events:none}</style></head>
<body><div id="m">__LOADING__</div>
<script src="https://cdn.jsdelivr.net/npm/three@0.147.0/build/three.min.js"></script>
<script>
(function(){
var LAT=__LAT__,LON=__LON__,INTER=__INTER__,Z=INTER?3:2,RAD=Math.PI/180,KB=true,borders=null;
var renderer=new THREE.WebGLRenderer({antialias:true,alpha:true});
renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,2));
renderer.setSize(innerWidth,innerHeight);
document.body.appendChild(renderer.domElement);
var cv=renderer.domElement;
var scene=new THREE.Scene();
var camera=new THREE.PerspectiveCamera(35,innerWidth/innerHeight,.1,50);
function fit(){var ha=Math.atan(Math.tan(17.5*RAD)*Math.min(1,camera.aspect));return Math.max(3.4,1.07/Math.sin(ha));}
camera.position.z=fit();
function xyz(lat,lon,r){var a=lat*RAD,b=lon*RAD;return new THREE.Vector3(r*Math.cos(a)*Math.sin(b),r*Math.sin(a),r*Math.cos(a)*Math.cos(b));}
function tex(s){var t=s.tagName?new THREE.CanvasTexture(s):s;t.generateMipmaps=false;t.minFilter=THREE.LinearFilter;t.magFilter=THREE.LinearFilter;return t;}
function blank(col){var c=document.createElement('canvas');c.width=c.height=2;var x=c.getContext('2d');if(col){x.fillStyle=col;x.fillRect(0,0,2,2);}return tex(c);}
var uni={day:{value:blank('#0a2a4a')},cloud:{value:blank()},rain:{value:blank()},sun:{value:new THREE.Vector3(1,0,0)},kc:{value:1},kr:{value:1}};
var earth=new THREE.Mesh(new THREE.SphereGeometry(1,96,64),new THREE.ShaderMaterial({uniforms:uni,
vertexShader:'varying vec3 p;void main(){p=position;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}',
fragmentShader:'precision highp float;varying vec3 p;uniform sampler2D day,cloud,rain;uniform vec3 sun;uniform float kc,kr;const float PI=3.14159265;void main(){vec3 n=normalize(p);float lat=asin(clamp(n.y,-1.,1.)),lon=atan(n.x,n.z);vec2 u=vec2(lon/(2.*PI)+.5,lat/PI+.5);vec3 c=texture2D(day,u).rgb;float l=smoothstep(-.12,.2,dot(n,sun));vec3 col=c*(.14+.86*l);if(abs(lat)<1.4835){vec2 m=vec2(u.x,log(tan(PI/4.+lat/2.))/(2.*PI)+.5);vec4 s=texture2D(cloud,m);float a=smoothstep(.3,.75,dot(s.rgb,vec3(.333)))*s.a*kc*.9;col=mix(col,vec3(.95)*(.2+.8*l),a);vec4 r=texture2D(rain,m);col=mix(col,r.rgb*1.15,clamp(r.a*1.2,0.,1.)*kr);}gl_FragColor=vec4(col,1.);}'}));
var atm=new THREE.Mesh(new THREE.SphereGeometry(1.09,64,48),new THREE.ShaderMaterial({transparent:true,side:THREE.BackSide,blending:THREE.AdditiveBlending,depthWrite:false,
vertexShader:'varying vec3 q;void main(){q=normalize(normalMatrix*normal);gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}',
fragmentShader:'varying vec3 q;void main(){float i=pow(max(.62-dot(q,vec3(0.,0.,1.)),0.),2.);gl_FragColor=vec4(.3,.6,1.,1.)*i;}'}));
scene.add(atm);
var root=new THREE.Group(),globe=new THREE.Group();
root.add(globe);scene.add(root);globe.add(earth);
var dotW=new THREE.Mesh(new THREE.SphereGeometry(.026,24,16),new THREE.MeshBasicMaterial({color:0xffffff}));dotW.position.copy(xyz(LAT,LON,1.004));
var dotB=new THREE.Mesh(new THREE.SphereGeometry(.019,24,16),new THREE.MeshBasicMaterial({color:0x2196f3}));dotB.position.copy(xyz(LAT,LON,1.012));
var halo=new THREE.Mesh(new THREE.SphereGeometry(.05,24,16),new THREE.MeshBasicMaterial({color:0x2196f3,transparent:true,opacity:.3,depthWrite:false}));halo.position.copy(xyz(LAT,LON,1.004));
globe.add(dotW);globe.add(dotB);globe.add(halo);
var qx=new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(1,0,0),LAT*RAD),qy=new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0,1,0),-LON*RAD);
globe.quaternion.copy(qx.multiply(qy));
function sunDir(){var d=Date.now()/864e5-10957.5,M=(357.5291+.98560028*d)*RAD,lam=(280.459+.98564736*d)*RAD+(1.915*Math.sin(M)+.02*Math.sin(2*M))*RAD,eps=23.4393*RAD,dec=Math.asin(Math.sin(eps)*Math.sin(lam)),ra=Math.atan2(Math.cos(eps)*Math.sin(lam),Math.cos(lam)),gm=(280.46061837+360.98564736629*d)*RAD;return xyz(dec/RAD,(ra-gm)/RAD,1);}
var loader=new THREE.TextureLoader();loader.setCrossOrigin('anonymous');
function ld(u,f){loader.load(u,function(t){uni.day.value=tex(t);document.getElementById('m').style.display='none';},undefined,f);}
ld('https://unpkg.com/three-globe/example/img/earth-blue-marble.jpg',function(){ld('https://cdn.jsdelivr.net/npm/three-globe/example/img/earth-blue-marble.jpg',function(){});});
fetch('https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_110m_admin_0_countries.geojson').then(function(r){return r.json();}).then(function(g){
var pts=[];
function ring(r){for(var i=0;i<r.length-1;i++){var a=xyz(r[i][1],r[i][0],1.002),b=xyz(r[i+1][1],r[i+1][0],1.002);pts.push(a.x,a.y,a.z,b.x,b.y,b.z);}}
g.features.forEach(function(f){var G=f.geometry;if(!G)return;var P=G.type=='Polygon'?[G.coordinates]:G.coordinates;P.forEach(function(poly){poly.forEach(ring);});});
var geo=new THREE.BufferGeometry();geo.setAttribute('position',new THREE.Float32BufferAttribute(pts,3));
borders=new THREE.LineSegments(geo,new THREE.LineBasicMaterial({color:0xffffff,transparent:true,opacity:.6}));borders.visible=KB;globe.add(borders);
}).catch(function(){});
function tiles(host,path,col,cb){var n=1<<Z,S=256,c=document.createElement('canvas');c.width=c.height=S*n;var g=c.getContext('2d'),left=n*n;
for(var x=0;x<n;x++)for(var y=0;y<n;y++)(function(x,y){var im=new Image();im.crossOrigin='anonymous';
im.onload=function(){g.drawImage(im,x*S,y*S);if(--left==0)cb(c);};
im.onerror=function(){if(--left==0)cb(c);};
im.src=host+path+'/256/'+Z+'/'+x+'/'+y+'/'+col+'.png';})(x,y);}
function load(){fetch('https://api.rainviewer.com/public/weather-maps.json').then(function(r){return r.json();}).then(function(j){
var ra=j.radar&&j.radar.past,sa=j.satellite&&j.satellite.infrared;
if(ra&&ra.length)tiles(j.host,ra[ra.length-1].path,'2/1_1',function(c){uni.rain.value=tex(c);});
if(sa&&sa.length)tiles(j.host,sa[sa.length-1].path,'0/0_0',function(c){uni.cloud.value=tex(c);});
}).catch(function(){});}
load();setInterval(load,600000);
window.setLayer=function(n,v){if(n=='clouds')uni.kc.value=v?1:0;if(n=='rain')uni.kr.value=v?1:0;if(n=='borders'){KB=v;if(borders)borders.visible=v;}};
if(INTER){
var ptr={},ids=function(){return Object.keys(ptr);};
function dist(){var k=ids(),a=ptr[k[0]],b=ptr[k[1]];return Math.hypot(a[0]-b[0],a[1]-b[1]);}
cv.addEventListener('pointerdown',function(e){cv.setPointerCapture(e.pointerId);ptr[e.pointerId]=[e.clientX,e.clientY];});
cv.addEventListener('pointermove',function(e){var p=ptr[e.pointerId];if(!p)return;var multi=ids().length>1,before=multi?dist():0;var dx=e.clientX-p[0],dy=e.clientY-p[1];ptr[e.pointerId]=[e.clientX,e.clientY];
if(multi){var d=dist();if(d>0&&before>0)camera.position.z=Math.max(1.7,Math.min(fit()*1.25,camera.position.z*before/d));}
else{var k=.0055*(camera.position.z-.85)/2.6,qa=new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0,1,0),dx*k),qb=new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(1,0,0),dy*k);globe.quaternion.premultiply(qa).premultiply(qb);}});
function up(e){delete ptr[e.pointerId];}
cv.addEventListener('pointerup',up);cv.addEventListener('pointercancel',up);
}
addEventListener('resize',function(){renderer.setSize(innerWidth,innerHeight);camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();if(!INTER)camera.position.z=fit();});
var t0=performance.now(),sT=0;
function frame(now){var t=(now-t0)/1000;
if(!INTER){root.rotation.y=Math.sin(t*.5)*.14;root.rotation.x=Math.sin(t*.37)*.05;}
var ph=(t*1.2)%1;halo.scale.setScalar(1+.7*ph);halo.material.opacity=.35*(1-ph);
if(!sT||now-sT>60000){uni.sun.value.copy(sunDir());sT=now;}
renderer.render(scene,camera);requestAnimationFrame(frame);}
requestAnimationFrame(frame);
})();
</script></body></html>
"""
}
