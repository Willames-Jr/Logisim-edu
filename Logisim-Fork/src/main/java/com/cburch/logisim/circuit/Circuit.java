/* Copyright (c) 2010, Carl Burch. License information is located in the
 * com.cburch.logisim.Main source code and at www.cburch.com/logisim/. */

package com.cburch.logisim.circuit;

import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.cburch.logisim.circuit.appear.CircuitAppearance;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentEvent;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.ComponentListener;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Clock;
import com.cburch.logisim.std.wiring.ProgrammableGenerator;
import com.cburch.logisim.util.CollectionUtil;
import com.cburch.logisim.util.EventSourceWeakSupport;

public class Circuit {
	private class EndChangedTransaction extends CircuitTransaction {
		private Component comp;
		private Map<Location, EndData> toRemove;
		private Map<Location, EndData> toAdd;

		EndChangedTransaction(Component comp, Map<Location, EndData> toRemove, Map<Location, EndData> toAdd) {
			this.comp = comp;
			this.toRemove = toRemove;
			this.toAdd = toAdd;
		}

		@Override
		protected Map<Circuit, Integer> getAccessedCircuits() {
			return Collections.singletonMap(Circuit.this, READ_WRITE);
		}

		@Override
		protected void run(CircuitMutator mutator, Project proj) {
			for (Location loc : toRemove.keySet()) {
				EndData removed = toRemove.get(loc);
				EndData replaced = toAdd.remove(loc);
				if (replaced == null) {
					wires.remove(comp, removed);
				} else if (!replaced.equals(removed)) {
					wires.replace(comp, removed, replaced);
				}
			}
			for (EndData end : toAdd.values()) {
				wires.add(comp, end);
			}
			((CircuitMutatorImpl) mutator).markModified(Circuit.this);
		}
	}

	private class MyComponentListener implements ComponentListener {
		@Override
		public void componentInvalidated(ComponentEvent e) {
			fireEvent(CircuitEvent.ACTION_INVALIDATE, e.getSource());
		}

		@Override
		public void endChanged(ComponentEvent e) {
			locker.checkForWritePermission("ends changed");
			Component comp = e.getSource();
			HashMap<Location, EndData> toRemove = toMap(e.getOldData());
			HashMap<Location, EndData> toAdd = toMap(e.getData());
			EndChangedTransaction xn = new EndChangedTransaction(comp, toRemove, toAdd);
			locker.execute(xn);
			fireEvent(CircuitEvent.ACTION_INVALIDATE, comp);
		}

		private HashMap<Location, EndData> toMap(Object val) {
			HashMap<Location, EndData> map = new HashMap<Location, EndData>();
			if (val instanceof List) {
				List<EndData> valList = (List<EndData>) val;
				for (EndData end : valList) {
					if (end != null) {
						map.put(end.getLocation(), end);
					}
				}
			} else if (val instanceof EndData) {
				EndData end = (EndData) val;
				map.put(end.getLocation(), end);
			}
			return map;
		}
	}

	//
	// helper methods for other classes in package
	//
	public static boolean isInput(Component comp) {
		return comp.getEnd(0).getType() != EndData.INPUT_ONLY;
	}

	private MyComponentListener myComponentListener = new MyComponentListener();
	private CircuitAppearance appearance;
	private AttributeSet staticAttrs;
	private SubcircuitFactory subcircuitFactory;
	private EventSourceWeakSupport<CircuitListener> listeners = new EventSourceWeakSupport<CircuitListener>();
	private CopyOnWriteArrayList<Component> comps = new CopyOnWriteArrayList<Component>(); // doesn't
	// include
	// wires
	CircuitWires wires = new CircuitWires();
	// wires is package-protected for CircuitState and Analyze only.
	private CopyOnWriteArrayList<Component> clocks = new CopyOnWriteArrayList<Component>();
	private CopyOnWriteArrayList<Component> programmablegenerators = new CopyOnWriteArrayList<Component>();
	private CircuitLocker locker;

	private WeakHashMap<Component, Circuit> circuitsUsingThis;

	public Circuit(String name) {
		appearance = new CircuitAppearance(this);
		staticAttrs = CircuitAttributes.createBaseAttrs(this, name);
		subcircuitFactory = new SubcircuitFactory(this);
		locker = new CircuitLocker();
		circuitsUsingThis = new WeakHashMap<Component, Circuit>();
	}

	//
	// Listener methods
	//
	public void addCircuitListener(CircuitListener what) {
		listeners.add(what);
	}

	public boolean contains(Component c) {
		return comps.contains(c) || wires.getWires().contains(c);
	}

	//
	// Graphics methods
	//
	public void draw(ComponentDrawContext context, Collection<Component> hidden) {
		Graphics g = context.getGraphics();
		Graphics g_copy = g.create();
		context.setGraphics(g_copy);
		wires.draw(context, hidden);

		if (hidden == null || hidden.size() == 0) {
			for (Component c : comps) {
				Graphics g_new = g.create();
				context.setGraphics(g_new);
				g_copy.dispose();
				g_copy = g_new;

				c.draw(context);
			}
		} else {
			for (Component c : comps) {
				if (!hidden.contains(c)) {
					Graphics g_new = g.create();
					context.setGraphics(g_new);
					g_copy.dispose();
					g_copy = g_new;

					try {
						c.draw(context);
					} catch (RuntimeException e) {
						// this is a JAR developer error - display it and move
						// on
						e.printStackTrace();
					}
				}
			}
		}
		context.setGraphics(g);
		g_copy.dispose();
	}

	private void fireEvent(CircuitEvent event) {
		for (CircuitListener l : listeners) {
			l.circuitChanged(event);
		}
	}

	void fireEvent(int action, Object data) {
		fireEvent(new CircuitEvent(action, this, data));
	}

	public Collection<Component> getAllContaining(Location pt) {
		HashSet<Component> ret = new HashSet<Component>();
		for (Component comp : getComponents()) {
			if (comp.contains(pt))
				ret.add(comp);
		}
		return ret;
	}

	public Collection<Component> getAllContaining(Location pt, Graphics g) {
		HashSet<Component> ret = new HashSet<Component>();
		for (Component comp : getComponents()) {
			if (comp.contains(pt, g))
				ret.add(comp);
		}
		return ret;
	}

	public Collection<Component> getAllWithin(Bounds bds) {
		HashSet<Component> ret = new HashSet<Component>();
		for (Component comp : getComponents()) {
			if (bds.contains(comp.getBounds()))
				ret.add(comp);
		}
		return ret;
	}

	public Collection<Component> getAllWithin(Bounds bds, Graphics g) {
		HashSet<Component> ret = new HashSet<Component>();
		for (Component comp : getComponents()) {
			if (bds.contains(comp.getBounds(g)))
				ret.add(comp);
		}
		return ret;
	}

	public CircuitAppearance getAppearance() {
		return appearance;
	}

	public Bounds getBounds() {
		Bounds wireBounds = wires.getWireBounds();
		Iterator<Component> it = comps.iterator();
		if (!it.hasNext())
			return wireBounds;
		Bounds firstBounds = it.next().getBounds();
		int xMin = firstBounds.getX();
		int yMin = firstBounds.getY();
		int xMax = xMin + firstBounds.getWidth();
		int yMax = yMin + firstBounds.getHeight();
		while (it.hasNext()) {
			Component c = it.next();
			Bounds bds = c.getBounds();
			int x0 = bds.getX();
			int x1 = x0 + bds.getWidth();
			int y0 = bds.getY();
			int y1 = y0 + bds.getHeight();
			if (x0 < xMin)
				xMin = x0;
			if (x1 > xMax)
				xMax = x1;
			if (y0 < yMin)
				yMin = y0;
			if (y1 > yMax)
				yMax = y1;
		}
		Bounds compBounds = Bounds.create(xMin, yMin, xMax - xMin, yMax - yMin);
		if (wireBounds.getWidth() == 0 || wireBounds.getHeight() == 0) {
			return compBounds;
		} else {
			return compBounds.add(wireBounds);
		}
	}

	public Bounds getBounds(Graphics g) {
		Bounds ret = wires.getWireBounds();
		int xMin = ret.getX();
		int yMin = ret.getY();
		int xMax = xMin + ret.getWidth();
		int yMax = yMin + ret.getHeight();
		if (ret == Bounds.EMPTY_BOUNDS) {
			xMin = Integer.MAX_VALUE;
			yMin = Integer.MAX_VALUE;
			xMax = Integer.MIN_VALUE;
			yMax = Integer.MIN_VALUE;
		}
		for (Component c : comps) {
			Bounds bds = c.getBounds(g);
			if (bds != null && bds != Bounds.EMPTY_BOUNDS) {
				int x0 = bds.getX();
				int x1 = x0 + bds.getWidth();
				int y0 = bds.getY();
				int y1 = y0 + bds.getHeight();
				if (x0 < xMin)
					xMin = x0;
				if (x1 > xMax)
					xMax = x1;
				if (y0 < yMin)
					yMin = y0;
				if (y1 > yMax)
					yMax = y1;
			}
		}
		if (xMin > xMax || yMin > yMax)
			return Bounds.EMPTY_BOUNDS;
		return Bounds.create(xMin, yMin, xMax - xMin, yMax - yMin);
	}

	public Collection<Circuit> getCircuitsUsingThis() {
		return circuitsUsingThis.values();
	}

	CopyOnWriteArrayList<Component> getClocks() {
		return clocks;
	}

	public Set<Component> getComponents() {
		return CollectionUtil.createUnmodifiableSetUnion(new HashSet<Component>(comps),
				new HashSet<Wire>(wires.getWires()));
	}

	public Collection<? extends Component> getComponents(Location loc) {
		return wires.points.getComponents(loc);
	}

	public Component getExclusive(Location loc) {
		return wires.points.getExclusive(loc);
	}

	CircuitLocker getLocker() {
		return locker;
	}
	
	public String toExpression() {
		Set<Component> comp =  this.getComponents();
		ArrayList<Component> outs = new ArrayList<>();
		ArrayList<Component> ints = new ArrayList<>();
		ArrayList<Component> wires = new ArrayList<>();
		ArrayList<Component> gates = new ArrayList<>();
	
		comp.forEach(c -> {
			/* É possível descobrir se é um fio
			 * Wire
			 * Se é um portão
			 * <NOME> Gate
			 * Se é output/input
			 * Pin
			 */
			String type = c.getFactory().toString();
			if (type.equals("Pin")) {
				Boolean is_output = Boolean.valueOf(c.getAttributeSet().getValue(c.getAttributeSet().getAttribute("output")).toString());
				if (is_output) {
					outs.add(c);
				} else {
					ints.add(c);
				}
			} else if (type.equals("Wire")) {
				wires.add(c);
			} else {
				gates.add(c);
			}
		});
		outs.forEach(out -> {
			ArrayList<Component> previousComponents = new ArrayList<>();
			System.out.println(test(comp, out));
		});
		return "";
	}
	private ArrayList<Component> previousComponent(Set<Component> comps, Component initialComp) {
		ArrayList<Component> endComps = new ArrayList<>();
		for (Component comp : comps) {
			if (comp.endsAt(initialComp.getLocation())) {
				endComps.add(comp);
				break;
			}
		}
		return endComps;
	}
	
	private String test(Set<Component> comps, Component initialComp) {
		ArrayList<Component> endComps = new ArrayList<>();
		String returnString = "(";
		String operator = initialComp.getFactory().toString().equals("AND Gate") ? " " : "+";
		// Dando loop infinito sempre chamando a funbção
		System.out.println("olá");
		for (Component comp : comps) {
			if (comp.endsAt(initialComp.getLocation())) {
				while (comp.getFactory().toString().equals("Wire")) {
					for (Component aux : comps) {
						if (aux.getFactory().toString().equals("AND Gate")) {
							System.out.print("GAte: ");
							System.out.println(aux.endsAt(comp.getLocation()));
						}
						if (aux.endsAt(comp.getLocation())) {
							comp = aux;
							System.out.print("Mudei: ");
							System.out.print(comp.getFactory().toString());
							break;
						}
					}
				}
				
				endComps.add(comp);
			}
		}
		for (Component endComp : endComps) {
			Boolean is_output = Boolean.valueOf(endComp.getAttributeSet().getValue(endComp.getAttributeSet().getAttribute("output")).toString());
			if (endComp.getFactory().toString().equals("Pin") && !is_output) {
				returnString += endComp.getAttributeSet().getAttribute("label").toString() + operator;
			} else {
				returnString += test(comps, endComp);
			}	
		}
		returnString += ")";
		return returnString;
	}
	
	//
	// access methods
	//
	public String getName() {
		return staticAttrs.getValue(CircuitAttributes.NAME_ATTR);
	}

	public Set<Component> getNonWires() {
		return new HashSet<Component>(comps);
	}

	public Collection<? extends Component> getNonWires(Location loc) {
		return wires.points.getNonWires(loc);
	}

	CopyOnWriteArrayList<Component> getProgrammableGenerators() {
		return programmablegenerators;
	}

	public Collection<? extends Component> getSplitCauses(Location loc) {
		return wires.points.getSplitCauses(loc);
	}

	public Set<Location> getSplitLocations() {
		return wires.points.getSplitLocations();
	}

	public AttributeSet getStaticAttributes() {
		return staticAttrs;
	}

	public SubcircuitFactory getSubcircuitFactory() {
		return subcircuitFactory;
	}

	public BitWidth getWidth(Location p) {
		return wires.getWidth(p);
	}

	public Location getWidthDeterminant(Location p) {
		return wires.getWidthDeterminant(p);
	}

	public Set<WidthIncompatibilityData> getWidthIncompatibilityData() {
		return wires.getWidthIncompatibilityData();
	}

	public CopyOnWriteArrayList<Wire> getWires() {
		return wires.getWires();
	}

	public Collection<Wire> getWires(Location loc) {
		return wires.points.getWires(loc);
	}

	public WireSet getWireSet(Wire start) {
		return wires.getWireSet(start);
	}

	public boolean hasConflict(Component comp) {
		return wires.points.hasConflict(comp);
	}

	public boolean isConnected(Location loc, Component ignore) {
		for (Component o : wires.points.getComponents(loc)) {
			if (o != ignore)
				return true;
		}
		return false;
	}

	void mutatorAdd(Component c) {
		locker.checkForWritePermission("add");

		if (c instanceof Wire) {
			Wire w = (Wire) c;
			if (w.getEnd0().equals(w.getEnd1()))
				return;
			boolean added = wires.add(w);
			if (!added)
				return;
		} else {
			// add it into the circuit
			boolean added = comps.add(c);
			if (!added)
				return;

			wires.add(c);
			ComponentFactory factory = c.getFactory();
			if (factory instanceof Clock) {
				clocks.add(c);
			} else if (factory instanceof ProgrammableGenerator) {
				programmablegenerators.add(c);
			} else if (factory instanceof SubcircuitFactory) {
				SubcircuitFactory subcirc = (SubcircuitFactory) factory;
				subcirc.getSubcircuit().circuitsUsingThis.put(c, this);
			}
			c.addComponentListener(myComponentListener);
		}
		fireEvent(CircuitEvent.ACTION_ADD, c);
	}

	public void mutatorClear() {
		locker.checkForWritePermission("clear");

		Set<Component> oldComps = new HashSet<Component>(comps);
		comps = new CopyOnWriteArrayList<Component>();
		wires = new CircuitWires();
		clocks.clear();
		programmablegenerators.clear();
		for (Component comp : oldComps) {
			if (comp.getFactory() instanceof SubcircuitFactory) {
				SubcircuitFactory sub = (SubcircuitFactory) comp.getFactory();
				sub.getSubcircuit().circuitsUsingThis.remove(comp);
			}
		}
		fireEvent(CircuitEvent.ACTION_CLEAR, oldComps);
	}

	void mutatorRemove(Component c) {
		locker.checkForWritePermission("remove");

		if (c instanceof Wire) {
			wires.remove(c);
		} else {
			wires.remove(c);
			comps.remove(c);
			ComponentFactory factory = c.getFactory();
			if (factory instanceof Clock) {
				clocks.remove(c);
			} else if (factory instanceof ProgrammableGenerator) {
				programmablegenerators.remove(c);
			} else if (factory instanceof SubcircuitFactory) {
				SubcircuitFactory subcirc = (SubcircuitFactory) factory;
				subcirc.getSubcircuit().circuitsUsingThis.remove(c);
			}
			c.removeComponentListener(myComponentListener);
		}
		fireEvent(CircuitEvent.ACTION_REMOVE, c);
	}

	public void removeCircuitListener(CircuitListener what) {
		listeners.remove(what);
	}

	//
	// action methods
	//
	public void setName(String name) {
		staticAttrs.setValue(CircuitAttributes.NAME_ATTR, name);
	}

	@Override
	public String toString() {
		return staticAttrs.getValue(CircuitAttributes.NAME_ATTR);
	}
}
