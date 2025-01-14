/*
  Copyright 2006 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package sim.portrayal.grid;
import sim.portrayal.*;
import sim.portrayal.simple.*;
import sim.field.grid.*;
import java.awt.*;
import java.awt.geom.*;
import sim.util.*;
import java.util.*;
import sim.display.*;

/**
   Portrayal for 3D Dense grids: grids of Bags of objects.
   
   The 'location' passed
   into the DrawInfo2D handed to the SimplePortryal2D is a MutableInt3D.
*/

public class DenseGrid3DPortrayal2D extends ObjectGrid3DPortrayal2D
    {
    public DrawPolicy policy;

    public void setDrawPolicy(DrawPolicy policy)
        {
        this.policy = policy;
        }

    public DrawPolicy getDrawPolicy()
        {
        return policy;
        }

    public void setField(Object field)
        {
        if (field instanceof DenseGrid3D ) setFieldBypass(field);  // see ObjectGridPortrayal2D.setFieldBypass
        else throw new RuntimeException("Invalid field for DenseGrid3DPortrayal2D: " + field);
        }
        
    public Object getObjectLocation(Object object, GUIState gui)
        {
        synchronized(gui.state.schedule)
            {
            final DenseGrid3D field = (DenseGrid3D) this.field;
            if (field==null) return null;

            int maxX = field.getWidth(); 
            int maxY = field.getHeight();
            if (maxX == 0 || maxY == 0) return null;

            // find the object.
            for(int x=0; x < maxX; x++)
                {
                Object[][] fieldx = field.field[x];
                for(int y = 0; y < maxY; y++)
                    {
                    Object[] fieldxy = fieldx[y];
                    for(int z = 0; z < fieldxy.length; z++)
						{
	                    Bag objects = (Bag)(fieldxy[z]);
	                    if (objects == null || objects.size() == 0) continue;
	                    for(int i=0;i<objects.numObjs;i++)
	                        if (objects.objs[i] == object)  // found it!
	                            return new Int3D(x,y,z);
	                    }
                    }
                }
            return null;  // it wasn't there
            }
        }

    protected void hitOrDraw(Graphics2D graphics, DrawInfo2D info, Bag putInHere)
        {
        final DenseGrid3D field = (DenseGrid3D)(this.field);
        Bag policyBag = new Bag();

        if (field==null) return;
        
        boolean objectSelected = !selectedWrappers.isEmpty();
        Object selectedObject = (selectedWrapper == null ? null : selectedWrapper.getObject());

        // Scale graphics to desired shape -- according to p. 90 of Java2D book,
        // this will change the line widths etc. as well.  Maybe that's not what we
        // want.
        
        // first question: determine the range in which we need to draw.
        // We assume that we will fill exactly the info.draw rectangle.
        // We can do the item below because we're an expensive operation ourselves
        
        final int maxX = field.getWidth(); 
        final int maxY = field.getHeight();
        final int maxZ = field.getLength();
        if (maxX == 0 || maxY == 0 || maxZ == 0) return; 
        
        final double xScale = info.draw.width / maxX;
        final double yScale = info.draw.height / maxY;
        int startx = (int)((info.clip.x - info.draw.x) / xScale);
        int starty = (int)((info.clip.y - info.draw.y) / yScale); // assume that the X coordinate is proportional -- and yes, it's _width_
        int endx = /*startx +*/ (int)((info.clip.x - info.draw.x + info.clip.width) / xScale) + /*2*/ 1;  // with rounding, width be as much as 1 off
        int endy = /*starty +*/ (int)((info.clip.y - info.draw.y + info.clip.height) / yScale) + /*2*/ 1;  // with rounding, height be as much as 1 off

        DrawInfo2D newinfo = new DrawInfo2D(info.gui, info.fieldPortrayal, new Rectangle2D.Double(0,0, xScale, yScale), info.clip);  // we don't do further clipping 
        newinfo.precise = info.precise;
        newinfo.location = locationToPass;

        if (endx > maxX) endx = maxX;
        if (endy > maxY) endy = maxY;
        if( startx < 0 ) startx = 0;
        if( starty < 0 ) starty = 0;
        for(int x=startx;x<endx;x++)
            for(int y=starty;y<endy;y++)
            	for(int z=0; z < maxZ; z++)
                {
                Bag objects = field.field[x][y][z];
                                
                if (objects == null || objects.size() == 0) continue;

                if (policy != null & graphics != null)
                    {
                    policyBag.clear();  // fast
                    if (policy.objectToDraw(objects,policyBag))  // if this function returns FALSE, we should use objects as is, else use the policy bag.
                        objects = policyBag;  // returned TRUE, so we're going to use the modified policyBag instead.
                    }
                locationToPass.x = x;
                locationToPass.y = y;
                locationToPass.z = z;
                
                for(int i=0;i<objects.numObjs;i++)
                    {
                    final Object portrayedObject = objects.objs[i];
                    Portrayal p = getPortrayalForObject(portrayedObject);
                    if (!(p instanceof SimplePortrayal2D))
                        throw new RuntimeException("Unexpected Portrayal " + p + " for object " + 
                            portrayedObject + " -- expected a SimplePortrayal2D");
                    SimplePortrayal2D portrayal = (SimplePortrayal2D)p;
                                        
                    // translate --- the   + newinfo.width/2.0  etc. moves us to the center of the object
                    newinfo.draw.x = (int)(info.draw.x + (xScale) * x);
                    newinfo.draw.y = (int)(info.draw.y + (yScale) * y);
                    newinfo.draw.width = (int)(info.draw.x + (xScale) * (x+1)) - newinfo.draw.x;
                    newinfo.draw.height = (int)(info.draw.y + (yScale) * (y+1)) - newinfo.draw.y;
                                        
                    // adjust drawX and drawY to center
                    newinfo.draw.x += newinfo.draw.width / 2.0;
                    newinfo.draw.y += newinfo.draw.height / 2.0;
                                        
                    if (graphics == null)
                        {
                        if (portrayedObject != null && portrayal.hitObject(portrayedObject, newinfo))
                            putInHere.add(getWrapper(portrayedObject, new Int3D(x,y,z)));
                        }
                    else
                        {
                        // MacOS X 10.3 Panther has a bug which resets the clip, YUCK
                        //                    graphics.setClip(clip);
                        newinfo.selected = (objectSelected &&  // there's something there
                            (selectedObject==portrayedObject || selectedWrappers.get(portrayedObject) != null));
                        /*{
                          LocationWrapper wrapper = null;
                          if (selectedObject == portrayedObject) 
                          wrapper = selectedWrapper;
                          else wrapper = (LocationWrapper)(selectedWrappers.get(portrayedObject));
                          portrayal.setSelected(wrapper,true);
                          portrayal.draw(portrayedObject, graphics, newinfo);
                          portrayal.setSelected(wrapper,false);
                          }
                          else */ portrayal.draw(portrayedObject, graphics, newinfo);
                        }
                    }
                }
                
        drawGrid(graphics, xScale, yScale, maxX, maxY, info);
        drawBorder(graphics, xScale, info);
        }

    // Overrides ObjectGrid3DPortrayal2D
    Int3D searchForObject(Object object, Int3D loc)
        {
        DenseGrid3D field = (DenseGrid3D)(this.field);
        Object[][][] grid = field.field;
        if (grid[loc.x][loc.y][loc.z] != null)
            {
            Bag b = (Bag)(grid[loc.x][loc.y][loc.z]);
            if (b.contains(object))
                return new Int3D(loc.x, loc.y, loc.z);
            }
        field.getMooreLocations(loc.x, loc.y, loc.z, SEARCH_DISTANCE, Grid3D.TOROIDAL, false, xPos, yPos, zPos);  // we remove the origin: there might be a big useless bag there
        for(int i=0;i<xPos.numObjs;i++)
            {
            if (grid[xPos.get(i)][yPos.get(i)][zPos.get(i)] != null)
                {
                Bag b = (Bag)(grid[xPos.get(i)][yPos.get(i)][zPos.get(i)]);
                if (b.contains(object))
                    return new Int3D(xPos.get(i), yPos.get(i), zPos.get(i));
                }
            }
        return null;
        }

                
    final Message unknown = new Message("It's too costly to figure out where the object went.");
	// overrides same version in ObjectGrid3D
    public LocationWrapper getWrapper(Object object, Int3D location)
        {
        final DenseGrid3D field = (DenseGrid3D)(this.field);
        return new LocationWrapper(object, location, this)
            {
            public Object getLocation()
                { 
                Int3D loc = (Int3D) super.getLocation();
                if (field.field[loc.x][loc.y][loc.z] != null &&
                	field.field[loc.x][loc.y][loc.z].contains(getObject()))  // it's still there!
                    {
                    return loc;
                    }
                else
                    {
                    Int3D result = searchForObject(object, loc);
                    if (result != null)  // found it nearby
                        {
                        location = result;
                        return result;
                        }
                    else    // it's moved on!
                        {
                        return unknown;
                        }
                    }
                }
            
            public String getLocationName()
                {
                Object loc = getLocation();
                if (loc instanceof Int3D)
                    return ((Int3D)this.location).toCoordinates();
                else return "Location Unknown";
                }
            };
        }

    }
