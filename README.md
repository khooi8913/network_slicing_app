# ONOS Network Slicing Application
Link to [Paper](http://journals.sfu.ca/apan/index.php/apan/article/download/240/pdf_148)

**This is the prototype implementation for the paper: "Multi-tenant Network Slicing Technique over OpenFlow-based MPLS Networks".** <br>

**APAN Research Workshop 2018** <br>
Released on 18 October 2018. <br>

## *Note*
This repository is no longer supported, due to the deprecation of the Virtual Network Subsystem in ONOS version 2.0+. 

The application works well with ONOS versions 1.3+.

<!-- ## Paper Abstract
Network virtualization plays an important role in the modern Internet architecture. Various OpenFlow-based network slicing techniques have been proposed and implemented to achieve network virtualization. In this paper, we present a scalable network slicing technique to provide multi-tenant network slices over an OpenFlow-based Multi-Protocol Label Switching (MPLS) network. The proposed approach acts as an isolation mechanism between multiple tenants over the same physical infrastructure, presenting the tenants with independent address range, topology and network control functions via virtualization. The design and implementation of the network slicing technique are based on the virtual network subsystem of the Open Networking Operating System (ONOS), an open-source software-defined networking (SDN) controller. Preliminary evaluations are done to verify that the proposed technique is able to perform address virtualization in a multi-tenant network environment. -->

## Project Description
The application is developed as an ONOS application through the installable through the [ONOS Application Subsystem](https://wiki.onosproject.org/display/ONOS/Application+Subsystem). At the time of development, ONOS `1.14.1` was mainly used for testing. However, the application should play well with ONOS versions `1.13+` up until other pre-ONOS `2.0` versions.

Reusing the limited APIs exposed by the ONOS Virtual Network Subsystem, this application provides ONOS with the capability of provisioning and managing virtual networks on top of physical infrastructure through repurposing the MPLS headers (OpenFlow version 1.3+ support needed).

Each tenant has the ability to define multiple virtual networks. Each virtual network consists of multiple switches, links which are shared across multiple tenant networks, as well as dedicated ports for each tenant network. 

Apart from L2 forwarding, L3 routing within a tenant network is made possible through the implementation of virtual gateways. Limited access control functionality is also present. If redundant paths are available, fast failover will take place to ensure undisrupted communication. 

Experiments/ tests were carried out on a Mininet emulated virtual network environemnt, with Open vSwitch running OpenFlow version 1.3.

For more detailed information, please refer to the documents under `/res`.

## Usage
### Step 1: Compile & install the application
Refer to the general guide [here](https://wiki.onosproject.org/display/ONOS/Template+Application+Tutorial), clone this repository, compile it with `mvn clean install` then install the `.oar` to ONOS. Activate the app on ONOS with the command `app activate org.xzk.network_slicing`.

### Step 2: Bring up Mininet and connect to ONOS
You may define your own topology according to your own needs. Connect the switches to your ONOS controller.

### Step 3: Creating tenants and virtual networks
All the commands that come with the application has the prefix `ns-*`. You may refer to the documents in `/res` for usage samples. 

If you are having a L3 network, make sure you configure the a virtual gateway, as well as setting a static MAC on your hosts pointing to the virtual gateway. 

### Step 4: Ping!
Ping between hosts within a tenant's virtual network. Have fun!

## Citation
If you find this work useful to your research, please cite:
```
@article{article,
author = {Khooi, Xin Zhe and Risdianto, Aris and Chong, Chun Yong and Ling, Teck Chaw},
year = {2018},
month = {08},
pages = {8-13},
title = {Multi-tenant Network Slicing Technique over OpenFlow-based MPLS Networks},
volume = {46}
}
```

## Feedback & Support
Suggestions and opinions on this work are welcomed.

Please feel free to contact the authors via email for support & feedback.

## License & Copyright
This project is open source under the Apache-2 License.

