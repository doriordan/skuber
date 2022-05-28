brew install minikube
# make sure docker is running
minikube start --kubernetes-version=v1.19.6 --driver=docker
kubectl config get-contexts
kubectl config use-context minikube
minikube status