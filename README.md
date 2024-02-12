## About
Plumago is a PlantUML generator eclipse plugin.

It uses the JDT parser possiblities and generates PlantUML files

## Minimum Eclipse Version
Plumago is completely `e4` based - which has a side effect:

Because of using dependency injection the changes from `javax.inject` to `jakarta.inject` have impact as well.
Starting with eclipse 2023-12 the eclipse SDK does provide `jakarta.inject`. The old way (`javax.inject`) is somehow provided as a fallback (but only for 2 years). 
The plugin uses `jakarta.inject` so it is necessary to use eclipse 2023-12 as the minimum version.

_Interesting point: E3 plugins don't have this problem because they don't have dependency injection. So if you're thinking about migrating plugins from e3 to e4, you have to worry about the fact that old versions of Eclipse could then become no longer supported by your plugin!_

## Installation
Use Eclipse marketplace integration or look at https://marketplace.eclipse.org/content/plumago

## Usage
Select a java class inside the project explorer and open the context menu

`Plumago` -> `Generate PlantUML`
