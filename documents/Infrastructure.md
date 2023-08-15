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
With an additional possibility to deploy a secondary VPC in another region. <br>
The CloudFormatiom stack for the VPC creation can be found in the `cloudFormationStacks` folder in the [1-vpc.yaml](./../cloudFormationStacks/1-vpc.yaml) file.<br>
It creates all the necessary Subnets, route tables, Internet gateway, and route table association. 

### Cross Region VPC

This VPC stack has the ability to deploy a cross region by setting the `DisasterRecovery` parameter to `yes`.<br>
The parameter is then evaluated in the `Condition` block and if it sets DisasterRecovery additional resources with the matched Conditon will be deployed in this and other stacks.<br>

```yaml
...
Paremeter:
  DisasterRecovery:
    Type: String
    AllowedValues: [yes, no]
    Default: no
...
Conditions:
  DisasterRecovery: !Equals [!Ref DisasterRecovery, yes]
  NoDisasterRecovery: !Equals [!Ref DisasterRecovery, no]
```
By default the cross region VPC is set to `no` and id evaluated as `NoDisasterRecovery` hence additional resources will **not** to be created. <br>
Only then when the Condition is Evaluated and set to `DisasterRecovery` will the additional VPC and subnets with the condition statement be created. <br>

```yaml
...
Resources:
  VPC2:
    Type: "AWS::EC2::VPC"
    Condition: DisasterRecovery 
    Properties:
      CidrBlock: !Ref VpcCIDR2
      EnableDnsHostnames: true
      EnableDnsSupport: true
      Tags:
        - Key: Name
          Value: !Ref VpcName
...
Output:
  DisasterRecovery:
    Description: Create Cross region RDS
    Value: !Ref DisasterRecovery
    Export: 
      Name: DisasterRecovery
```
The above value is exported in CloudFormation so that susequent stacks are able to fetch the same value and deploy their disaster recovery stacks accordingly.

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

