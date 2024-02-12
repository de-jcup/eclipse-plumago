## About
Plumago is a PlantUML generator eclipse plugin.

It uses the JDT parser possiblities and generates PlantUML files

## Minimum Eclipse Version
Plumago is completele e4 based - which has a side effect:

Because of using dependency injection the changes from `javax.inject` to `jakarta.inject` have here impact as well.
Starting with eclipse 2023-12 the eclipse SDK does provide `jakarta.inject`. The old way (`javax.inject`) is somehow provided as a fallback (but only for 2 years). 
The plugin uses `jakarta.inject` so it is necessary to use eclipse 2023-12 as the minimum version.

## Usage
Select a java class inside the project explorer and open the context menu

"Plumago" -> "Generate PlantUML"
