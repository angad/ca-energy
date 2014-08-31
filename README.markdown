# California Energy consumption data visualization

An app that visualizes data from http://www.ecdms.energy.ca.gov/ using Google Charts

Built using Finatra - A Finagle based web framework written in Scala.

# Data

This reposiory does not contain the data. To get the data and make it work with this project -

* Download the data from the above website
* Put it in data/ folder
* rename all the files to lower case

Drop me an email if you have trouble getting the data.

The data contains

* Electricity and Gas consumption by
    * County
        * Residential
        * Non-Residential
    * Planning Area
    * Utility

# Current Status

(30th Aug 2014) - Line graph for each county and planning area

# TODO

* Graphs comparing each county and planning area for both Electricity and Gas
* Ajax-ify list view
* Implement "by Utility" consumption
* Pi-chart for county - which county consumes the most
