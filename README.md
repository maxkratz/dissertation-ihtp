# Dissertation - IHTP/IHTC GIPS Projects

[**GIPS**](https://github.com/Echtzeitsysteme/gips) is an open-source framework for **G**raph-Based (M)**I**LP **P**roblem **S**pecification.

This repository holds my dissertation-related IHTP/IHTC GIPS projects.
It was forked from the [gips-examples repository](https://github.com/Echtzeitsysteme/gips-examples).


## Setup

* Install [GIPS](https://github.com/Echtzeitsysteme/gips) as described in its [repository](https://github.com/Echtzeitsysteme/gips).
* Launch a runtime workspace (while using a runtime Eclipse) as stated in the eMoflon::IBeX installation steps. (Please refer to the installation steps of GIPS above.)
* Use this [PSF file](https://raw.githubusercontent.com/maxkratz/disseration-ihtp/main/projectSet.psf) to import all of the dissertation-related projects.
* Build all your projects with the black eMoflon hammer. Sometimes, it is required to trigger a cleaning in Eclipse (*Project -> Clean... -> Clean all projects*).
* Runner projects contain runnable Java classes with a `main` function.


## Project Overview

| **Name**       | **Description**                                                                           |
| -------------- | ----------------------------------------------------------------------------------------- |
| `ihtcvirtual*` | Virtualized GIPS projects for solving the IHTP/IHTC 2024 (competition).                   |
| `ihtc*`        | Projects related to the (non-virtual) GIPS-based solution for the IHTC 2024 (competition) |

For more projects, refer to the [upstream repository](https://github.com/Echtzeitsysteme/gips-examples) or the [GIPS test repository](https://github.com/Echtzeitsysteme/gips-tests).


## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for more details.
