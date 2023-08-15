## Foreword

There are a lot of assumptions made on my part with this excercise. <br>
I started to have made a lot more assumptions when I started to create the infrastructure.  <br>
I quickly realised that my idea of how the infrastructure would look would take more than 2 days implement and needed to stay in my lane.<br>
As such I opted not to create some of the infrastructure code, and resort to explaining my ideas in the documentation. <br>
I find myself thinking about security and locking down infrastructure a lot, and this is a never ending journey in my experience. <br>
No naming of Tagging conventions were followed in this excercise. <br>

Eventhough a bit unconventional, the documentation is completely done in Markdown language. <br>
The reason for this choice is the idea of having a singular source of truth for both code and documentation. <br>
Additionally, exporting, moving documentation and code becomes much easier. <br> 
The entirety of this document can be commited to a git repo, cloned and accessed much easier than hosting documentation in a propiratary format. <br>
At the end of the day the documentation can be exported and moved from a common format to anywhere one wishes.

## Infrastructure Problem Documentation

Architectural design and proposed solution of all the application components.<br>
[Infrastructure documentation.](./documents/Infrastructure.md)


## DevOps Problem Documentation

Documantation, description, and design for the CI/CD pipeline deploying a Node.js application into AWS Elastic Beanstalk.<br>
[DevOps documentation.](./documents/devops.md)<br>
Please be aware that the code has `not` been fully tested, and was created to reflect the knowledge and ability to work within the AWS infrastructure and using CI/CD pilelines in Jenkins.


## Observations

This excercise was a good reminder about the limitations of CloudFormation and Jenkins being used for Infrastructure as Code.<br>
With in this excercise and the PSQL redundancy that I created using CloudFormation, I mimicked a redundancy model that I had previously designed for another company using Terraform. <br>
In my observations I have found CloudFormation to be very rigid, albeit a lot simpler to work with than Terraform.<br> 
Having said that Terraform provides a level of flexibility allowing creation of dynamic infrastructure as code, this is not as easily accessible in a CLoudFormation stack.<br>
With terraform and a custom designed RDS module a disaster recovery situation can be turned from an outage to a brief interruption in service. <br>
The same could be accomplished with CLoudFormation with less repeatable code however.<br>
I feel that Terraform allows for more dynamic scaling of resources and more flexibility for customisation.<br>
Addtionally, the pipeline for the the intergration and deployment would look much simpler and be easier to work with having used Terraform instead. <br>
I am of the opinion that although Jenkins still has a place in the industry, it is unnecessarilly complex and has a larger berrier to entry due to the steeper learning curve in creating CI/CD pipelines.<br> 

