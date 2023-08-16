# Table of Contents
[Proposed Architecture](#the-proposed-architecture)<br>
[Infrastructure Cost Estimate](#infrastructure-cost-estimate)<br>
[VPC Stack](#vpc-stack)<br>
[Elastic Beanstalk](#elastic-beanstalk)<br>
[PSQL RDS](#psql-rds)<br>
[DNS and Repos](#dns-and-repos)<br>
[Security and Compliance](#security-and-compliance)<br>
[Disaster Recovery](#disaster-recovery)<br>


## Proposed Architecture

Below you will find a [diagram](#diagram01) of the proposed architecture for a highly redundant and highly available Web Application.<br>
The ides is to provide redundancy both with the web application and with the backed database. <br>
The NodeJS application is containerized, static and able to scale with load. As such I feel like the database is the primary concern as for a disaster recovery situation. <br>

### Data Flow
1. User initiates a session to wwww.yourdomain.com
2. DNS resolution happens against Route53 in AWS, domain certificate is signed by the Certificate Manager
3. Traffic gets redirected to the CloudFront distribution, if GeoRestriction is active cloudfront analyzes the origin and allows or restricts traffic to the Load Balancer.
4. Load Balancer analyzes the target group and checks to see which ECS node service container is least busy and balances traffic accordingly. At this point some sort of session pinning occurs. 
5. Node.js container processes the users request, renders the page and request data from the database. 
6. Postgres instance processes the queries requested by the Node container and sends the requested data back to the origin.

###### diagram.01
![](../images/infrastructure.drawio.png)

## Infrastructure Cost Estimate

The estimated costs for the infrastructure were calculated as follows using the [AWS Cost Calculator](https://calculator.aws/). <br>
The estimate was done in USD instead of CAD and primarily using the `us-east-1` and `us-east-2` regions. <br>
Nothing in particular that prevented me from using Canadian regions, just a force of habit when working within AWS. <br>
I estimated for 3 EC2 instance costs instead of the desired 2 count.<br>


### Estimate Summary

This summary includes up front cost.

###### table.01
| Upfront cost | Monthly cost | Total 12 months cost | Currency
|---|--------------|-----|----|
| 0 | 1300.4499999999998 | 15605.40 | USD

### Detailed Estimate
###### table.02
|Region|Description|Service|Upfront|Monthly|First 12 months total|Currency|Status|Configuration summary|
|-----|-----|-----|-----|-----|-----|-----|-----|-----|
|US East (N. Virginia)|KMS key|AWS Key Management Service|0|11|132.00|USD||"Number of customer managed Customer Master Keys (CMK) (5)| Number of symmetric requests (2000000)"|
|US East (Ohio)|KMS replica|AWS Key Management Service|0|11|132.00|USD||"Number of customer managed Customer Master Keys (CMK) (5)| Number of symmetric requests (2000000)"|
|US East (N. Virginia)|Secrets|AWS Secrets Manager|0|1.2|14.40|USD||"Number of secrets (3)| Average duration of each secret (30 days)| Number of API calls (1 per month)"|
|US East (Ohio)|Secrets Replica|AWS Secrets Manager|0|1.2|14.40|USD||"Number of secrets (3)| Average duration of each secret (30 days)| Number of API calls (1 per month)"|
|US East (N. Virginia)|ECR|Amazon Elastic Container Registry|0|5.05|60.60|USD||"DT Inbound: Internet (3 gb_month)| DT Outbound: US East (Ohio) (5 gb_month)| Amount of data stored (50 GB per month)| Data transfer cost (0.05)"|
|US East (N. Virginia)|My Domain|Amazon Route 53|0|1.9|22.80|USD||"IP (CIDR) blocks (500)| Hosted Zones (3)"|
|US East (N. Virginia)|Web CDN |Amazon CloudFront|0|22|264.00|USD||"Data transfer out to internet (100 GB per month)| Number of requests (HTTPS) (500000 per month)| Data transfer out to origin (100 GB per month)| Data transfer out to internet (100 GB per month)| Data transfer out to origin (100 GB per month)| Number of requests (HTTPS) (500000 per month)"|
|US East (N. Virginia)|RDS primary|Amazon RDS for PostgreSQL|0|464.49|5573.88|USD||"Storage volume (General Purpose SSD (gp2))| Storage amount (50 GB)| Nodes (1)| Instance Type (db.m1.xlarge)| Utilization (On-Demand only) (100 %Utilized/Month)| Deployment Option (Single-AZ)| Pricing Model (OnDemand)| Cost for one month of retention (per vCPU per month) (1.5000000000)| Cost for each additional month of retention (per vCPU per month) (0.0631000000)| Cost for total retention (per vCPU per month) (1.56)| Additional backup storage (500 GB)| Total Size of Backup Processed for Export (GB) (350 per month)"|
|US East (Ohio)|RDS replica|Amazon RDS for PostgreSQL|0|138.61|1663.32|USD||"Storage volume (General Purpose SSD (gp2))| Storage amount (50 GB)| Nodes (1)| Instance Type (db.m4.large)| Utilization (On-Demand only) (100 %Utilized/Month)| Deployment Option (Single-AZ)| Pricing Model (OnDemand)"|
|US East (N. Virginia)||RDS|0|231.23855|2774.86|USD||"Total RDS storage (3000 GB)| Hourly backup retention period (0 Days)| Daily backup retention period (30 Days)| Expected daily change rate (0.02)"|
|US East (N. Virginia)|ECS Nodes|Amazon EC2 |0|412.764|4953.17|USD||"Tenancy (Shared Instances)| Operating system (Linux)| Workload (Consistent| Number of instances: 3)| Advance EC2 instance (t2.xlarge)| Pricing strategy (On-Demand Utilization: 100 %Utilized/Month)| Enable monitoring (enabled)| DT Inbound: Internet (0 tb_month)| DT Outbound: Amazon CloudFront (0 tb_month)| DT Intra-Region: (0 tb_month)"|


## VPC Stack 

The CloudFormatiom stack for the VPC creation can be found in the `cloudFormationStacks` folder in the [1-vpc.yaml](./../cloudFormationStacks/1-vpc.yaml) file.<br>

There are 3 tiers of subnets across 3 availability zones in a single region.<br>
With an additional possibility to deploy a secondary VPC in another region such as us-east-2 for redundancy. <br>
This stack creates all the necessary Subnets, route tables, Internet gateway, and route table association. <br>

### Cross Region VPC

This VPC stack has the ability to deploy to a cross region by setting the `DisasterRecovery` parameter to `yes`.<br>
The parameter is then evaluated in the `Condition` block and if this sets to DisasterRecovery additional resources with the matched Condition will be deployed.<br>

```yaml
...
Parameter:
  DisasterRecovery:
    Type: String
    AllowedValues: [yes, no]
    Default: no
...
Conditions:
  DisasterRecovery: !Equals [!Ref DisasterRecovery, yes]
  NoDisasterRecovery: !Equals [!Ref DisasterRecovery, no]
```
By default the cross region VPC is set to `no` and is evaluated as `NoDisasterRecovery` hence additional resources will **not** to be created. <br>
Only then when the Condition is Evaluated and set to `DisasterRecovery` will the additional VPC with subnets and replica RDS instance be created. <br>

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
The `DisasterRecovery` value is also exported so that subsequent stacks (RDS) are able to fetch the value and deploy their disaster recovery resources accordingly.

Subnets are arranged as follows in the main VCP.

###### table.03
| Subnet Tier | Purpose | IP Range |
|-------------|---------|----------|
| VPC   |   | `10.0.0.0/16` |
| `public` | hosts the BeanStalk load balancer | `10.0.1.0/24` - `10.0.3.0/24` | 
| `private`| hosts the BeanStalk web application | `10.0.4.0/24` - `10.0.6.0/24` | 
| `database`| used by the RDS subnet group | `10.0.7.0/24` - `10.0.9.0/24` |

<br>
When DisasterRecovery is enabled the secondary region VPC has the following IP values.

###### table.04
| Subnet Tier | Purpose | IP Range |
|-------------|---------|----------|
| VPC   |   | `10.10.0.0/16` |
| `public` | hosts the BeanStalk load balancer | `10.10.1.0/24` | 
| `private`| hosts the BeanStalk web application | `10.10.2.0/24` | 
| `database`| used by the RDS subnet group | `10.10.3.0/24` |

## Elastic Beanstalk

The Elastic Beanstalk stack can be found in the `cloudFormationStacks` folder in the [4-elastic-beanstalk.yaml](./../cloudFormationStacks/4-elastic-beanstalk.yaml) file.<br>

Within this Stack 2 `t2.xlarge` ECS instances are spun up to accommodate the Node.js web app containers. <br>
The Auto Scaling Group desired count is 2, and the maximum allowed is 6 instances for ECS hosting. <br>
```yaml
  ECSAutoScalingGroup:
    Type: "AWS::AutoScaling::AutoScalingGroup"
    Properties:
      AutoScalingGroupName: !Join ['', [!Ref EnvironmentName, '-ASG']]
      MinSize: "2"
      MaxSize: "6"
      DesiredCapacity: "2"
...
```

In addition to the above, this stack also deploys a CloudFront distribution. <br>
The Price class for the CDN is set to 100 which only caches content in the CDN for US, Canada, and Europe regions. <br>
If desired one can add Geo restrictions to the CDN using the following notation.<br>

```yaml 
...
    GeoRestriction:
        RestrictionType: whitelist
        Locations:
        - US
        - CA
```

## PSQL RDS

The CloudFormation stack for RDS creation can be found in the `cloudFormationStacks` folder in the [2-rds.yaml](./../cloudFormationStacks/2-rds.yaml) file.<br>

It is my assumption that the RDS instance is the primary data store for the application and that only logic is handled by the containerized web application. <br>
I have opted to use a single RDS instance. It may be a necessary to implement an additional reporting, or read replica in the primary Region.<br>
This will all depend on the use case for this application, and how the data will be used outside of the web application.<br>

As defined in the VPC When the value of `DisasterRecovery` set to `yes`, the CloudFormation template creates a cross region RDS PSQL read replica, KMS key replica, and Secrets replica. <br>
The `KMS` key is use to encrypt RDS data at rest, and can also be used to encrypt secrets.<br>

## DNS and Repos

The CloudFormation stack for the DNS and Repo creation can be found in the `cloudFormationStacks` folder in the [3-repos-dns.yaml](../cloudFormationStacks/3-repos-dns.yaml) file.<br>
This file contains the deployment of Route53 DNS zone, Certificate manager, ECR registry, and a Git Repo.<br>

`Route 53` handles the DNS entries and mapping to the Elastic Beanstalk IP Load Balancer.<br>
The `Certificate Manager` handles the https certificates for the DNS zone required to accept encrypted web traffic destined for the Web Application.<br>

## Security and Compliance

Once the RDS instance has been deployed an admin needs to go into the instance to change the master user and password, then update the secrets manager secret. <br>
There is a possibility of the stack containing the password or it being somehow exposed, no chances should be taken.<br>

Deploying a Content Delivery Network such as `CloudFront` to limit inbound traffic using GEO location is highly advisable. <br>
By setting the `PriceClass` in the CDN we can also enable caching for the website to improve performance. <br>
CloudFront provides multiple features to mitigate DDoS (Distributed Denial of Service) attacks, which are an increasingly common threat to web applications.<br>

Security group in the `private` subnets will only allow inbound traffic from the public subnets, specifically where the load balancer lives. <br>
In addition to this the security group in the `database` subnets only allow inbound traffic from `10.0.4.0/22` which covers only private subnets. <br> 
Data in RDS is encrypted at rest using a KMS key which when `DisasterRecovery` is enabled is replicated to another region alongside the database. <br>
ACLs in the VPC can also be leveraged to create stricter traffic policies inbound to the subnets.<br>

In addition to the above, WAF and Shield can also be deployed to analyze inbound traffic and provide further security. <br>
If cost is of no concern there are virtual security appliances that AWS can provide for even stricter policies. <br>

## Disaster Recovery

I have implemented a somewhat basic Disaster Recovery scenario where a small read replica for the RDS instance is deployed in another AWS region. <br>
Data is replicated to this instance from the primary read write RDS database. <br>
This instance can be scaled and turned into a read/write primary vary quickly. <br>
Then a new pipeline can be defined inside Jenkins that deploys the same CloudFormation stacks that exist in Region 01 in Region 02. <br>

In addition to this I have priced out an AWS backup that takes database snapshots and can store them for any length of time.<br>
I have found that AWS backup has more redundancies and options when it comes to taking RDS snapshots. <br>
If this is a 24/7 mission critical application and down time can not be accepted the instance backups should be taken from a read replica instead of the primary instance. <br>
This will ensure that when backups are performed no performance impacts or outages will occur on the primary instance. <br>

