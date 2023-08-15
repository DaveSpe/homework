# Table of Contents
[The Proposed Architecture](#the-proposed-architecture)<br>
[VPC](#vpc)<br>
[Security and Compliance](#security-and-compliance)<br>
[Security Groups](#security-groups)<br>
[Elastic Beanstalk](#elastic-beanstalk)<br>
[PSQL RDS](#psql-rds)<br>
[]()<br>

## The Proposed Architecture

Below you will find a [diagram](#diagram01) of the proposed architecture for a highly redundand and highly available Web Application.<br>
The ides is to provide redundancy both with the web application and with the backed database. <br>
The NodeJS application is containerized, static and able to scale with load. As such I feel like the database is the primary concern as far as disaster recovery is concerned. <br>

## VPC

We have 3 tiers of subnets accross 3 availibility zones in a single region.<br>
The CloudFormatiom stack for the VPC creation can be found in the `cloudFormationStacks` folder in the [1-vpc.yaml](./../cloudFormationStacks/1-vpc.yaml) file.<br>
The Cross region VPC has only one availability zone and can be created using a Condition with the variable `DisasterRecovery`.<br>
Settign the above variable to yes will also crate cross region replicas for the RDS instance (read only), KMS key used to encrypt data at rest, <br>and Secret Key which is used for RDS admin credentials.<br>

```yaml
  DisasterRecovery:
    Type: String
    AllowedValues: [yes, no]
    Default: no
```
The above value is exported in cloud formation so that susequent stacks are able to fetch it and deploy the disaster recovery stack accordingly.

The `public tier` hosts the Bean Stalk load balancer. 

## Security and Compliance

With the design the way it sits the load balancer will accept all traffic from all around the world.<br>
Deploying a Content Delivery Network such as `CloudFront` to limit where the traffic comes from would be highly advisable. <br>
A CDN is capable of mitigating DOS attachs, geo-restricting access to a website and also provides addtional insigts/logs in to the traffic against the website.<br>

### Security Groups

Security group in the `private` subnets will only allow inboud traffic from the public subnets, specifically where the load balancer lives. 
In addition to this the security group in the databse subnets only allows inbound traffic from `10.0.4.0/22` which covers 

## Elastic Beanstalk



## PSQL RDS

It is my assumption that the RDS instance is the primary data store for the application and that only logic is handled by the web application. 

In addition to to this I only opted to use a single RDS instance. There may be a neccessati to implement an additional reporting, or read replica in the primary Region.
These may be necessary to drive reporting and other insights into the data. 

When the value of `DisasterRecovery` set to `yes`, the CloudFormation template creates a cross region RDS PSQL read replica, KMS replica, and Secrets replica.
The `KMS` key is use to encrypt RDS data at rest.





The public tier hosts the load balancer and 
If the company chooses 

`Route 53` handles the DNS entries and mapping to the Elastic Beanstalk IP Load Balancer.<br>
The `Certificate Manager` handles the https certificates for the DNS zone required to acept encrypted web traffic destined for the Web Application.<br>
***
## Diagrams and Tables

###### diagram.01
![](../images/infrastructure.drawio.png)

